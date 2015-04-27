/* ====================================================================
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
==================================================================== */

package org.apache.poi.hslf.usermodel;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;

import org.apache.poi.ddf.*;
import org.apache.poi.hslf.exceptions.HSLFException;
import org.apache.poi.hslf.record.*;
import org.apache.poi.sl.draw.geom.*;
import org.apache.poi.sl.usermodel.*;
import org.apache.poi.sl.usermodel.StrokeStyle.LineCompound;
import org.apache.poi.sl.usermodel.StrokeStyle.LineDash;
import org.apache.poi.util.LittleEndian;
import org.apache.poi.util.Units;

/**
 *  An abstract simple (non-group) shape.
 *  This is the parent class for all primitive shapes like Line, Rectangle, etc.
 *
 *  @author Yegor Kozlov
 */
public abstract class HSLFSimpleShape extends HSLFShape implements SimpleShape {

    public final static double DEFAULT_LINE_WIDTH = 0.75;

    /**
     * Records stored in EscherClientDataRecord
     */
    protected Record[] _clientRecords;
    protected EscherClientDataRecord _clientData;

    /**
     * Create a SimpleShape object and initialize it from the supplied Record container.
     *
     * @param escherRecord    <code>EscherSpContainer</code> container which holds information about this shape
     * @param parent    the parent of the shape
     */
    protected HSLFSimpleShape(EscherContainerRecord escherRecord, ShapeContainer<HSLFShape> parent){
        super(escherRecord, parent);
    }

    /**
     * Create a new Shape
     *
     * @param isChild   <code>true</code> if the Line is inside a group, <code>false</code> otherwise
     * @return the record container which holds this shape
     */
    protected EscherContainerRecord createSpContainer(boolean isChild) {
        _escherContainer = new EscherContainerRecord();
        _escherContainer.setRecordId( EscherContainerRecord.SP_CONTAINER );
        _escherContainer.setOptions((short)15);

        EscherSpRecord sp = new EscherSpRecord();
        int flags = EscherSpRecord.FLAG_HAVEANCHOR | EscherSpRecord.FLAG_HASSHAPETYPE;
        if (isChild) flags |= EscherSpRecord.FLAG_CHILD;
        sp.setFlags(flags);
        _escherContainer.addChildRecord(sp);

        EscherOptRecord opt = new EscherOptRecord();
        opt.setRecordId(EscherOptRecord.RECORD_ID);
        _escherContainer.addChildRecord(opt);

        EscherRecord anchor;
        if(isChild) anchor = new EscherChildAnchorRecord();
        else {
            anchor = new EscherClientAnchorRecord();

            //hack. internal variable EscherClientAnchorRecord.shortRecord can be
            //initialized only in fillFields(). We need to set shortRecord=false;
            byte[] header = new byte[16];
            LittleEndian.putUShort(header, 0, 0);
            LittleEndian.putUShort(header, 2, 0);
            LittleEndian.putInt(header, 4, 8);
            anchor.fillFields(header, 0, null);
        }
        _escherContainer.addChildRecord(anchor);

        return _escherContainer;
    }

    /**
     *  Returns width of the line in in points
     */
    public double getLineWidth(){
        EscherOptRecord opt = getEscherOptRecord();
        EscherSimpleProperty prop = getEscherProperty(opt, EscherProperties.LINESTYLE__LINEWIDTH);
        double width = (prop == null) ? DEFAULT_LINE_WIDTH : Units.toPoints(prop.getPropertyValue());
        return width;
    }

    /**
     *  Sets the width of line in in points
     *  @param width  the width of line in in points
     */
    public void setLineWidth(double width){
        EscherOptRecord opt = getEscherOptRecord();
        setEscherProperty(opt, EscherProperties.LINESTYLE__LINEWIDTH, Units.toEMU(width));
    }

    /**
     * Sets the color of line
     *
     * @param color new color of the line
     */
    public void setLineColor(Color color){
        EscherOptRecord opt = getEscherOptRecord();
        if (color == null) {
            setEscherProperty(opt, EscherProperties.LINESTYLE__NOLINEDRAWDASH, 0x80000);
        } else {
            int rgb = new Color(color.getBlue(), color.getGreen(), color.getRed(), 0).getRGB();
            setEscherProperty(opt, EscherProperties.LINESTYLE__COLOR, rgb);
            setEscherProperty(opt, EscherProperties.LINESTYLE__NOLINEDRAWDASH, color == null ? 0x180010 : 0x180018);
        }
    }

    /**
     * @return color of the line. If color is not set returns <code>java.awt.Color.black</code>
     */
    public Color getLineColor(){
        EscherOptRecord opt = getEscherOptRecord();

        EscherSimpleProperty p = getEscherProperty(opt, EscherProperties.LINESTYLE__NOLINEDRAWDASH);
        if(p != null && (p.getPropertyValue() & 0x8) == 0) return null;

        Color clr = getColor(EscherProperties.LINESTYLE__COLOR, EscherProperties.LINESTYLE__OPACITY, -1);
        return clr == null ? Color.black : clr;
    }

    /**
     * Gets line dashing.
     *
     * @return dashing of the line.
     */
    public LineDash getLineDashing(){
        EscherOptRecord opt = getEscherOptRecord();
        EscherSimpleProperty prop = getEscherProperty(opt, EscherProperties.LINESTYLE__LINEDASHING);
        return (prop == null) ? LineDash.SOLID : LineDash.fromNativeId(prop.getPropertyValue());
    }

    /**
     * Sets line dashing.
     *
     * @param pen new style of the line.
     */
    public void setLineDashing(LineDash pen){
        EscherOptRecord opt = getEscherOptRecord();
        setEscherProperty(opt, EscherProperties.LINESTYLE__LINEDASHING, pen == LineDash.SOLID ? -1 : pen.nativeId);
    }

    /**
     * Gets the line compound style
     *
     * @return the compound style of the line.
     */
    public LineCompound getLineCompound() {
        EscherOptRecord opt = getEscherOptRecord();
        EscherSimpleProperty prop = getEscherProperty(opt, EscherProperties.LINESTYLE__LINESTYLE);
        return (prop == null) ? LineCompound.SINGLE : LineCompound.fromNativeId(prop.getPropertyValue());
    }
    
    /**
     * Sets the line compound style
     *
     * @param style new compound style of the line.
     */
    public void setLineCompound(LineCompound style){
        EscherOptRecord opt = getEscherOptRecord();
        setEscherProperty(opt, EscherProperties.LINESTYLE__LINESTYLE, style == LineCompound.SINGLE ? -1 : style.nativeId);
    }

    /**
     * Returns line style. One of the constants defined in this class.
     *
     * @return style of the line.
     */
    public StrokeStyle getStrokeStyle(){
        return new StrokeStyle() {
            public PaintStyle getPaint() {
                return null;
            }

            public LineCap getLineCap() {
                return null;
            }

            public LineDash getLineDash() {
                return null;
            }

            public LineCompound getLineCompound() {
                return null;
            }

            public double getLineWidth() {
                return 0;
            }
            
        };
    }

    /**
     * The color used to fill this shape.
     */
    public Color getFillColor(){
        return getFill().getForegroundColor();
    }

    /**
     * The color used to fill this shape.
     *
     * @param color the background color
     */
    public void setFillColor(Color color){
        getFill().setForegroundColor(color);
    }

    /**
     *
     * @return 'absolute' anchor of this shape relative to the parent sheet
     */
    public Rectangle2D getLogicalAnchor2D(){
        Rectangle2D anchor = getAnchor2D();

        //if it is a groupped shape see if we need to transform the coordinates
        if (getParent() != null){
            ArrayList<HSLFGroupShape> lst = new ArrayList<HSLFGroupShape>();
            for (ShapeContainer<HSLFShape> parent=this.getParent();
                parent instanceof HSLFGroupShape;
                parent = ((HSLFGroupShape)parent).getParent()) {
                lst.add(0, (HSLFGroupShape)parent);
            }
            
            AffineTransform tx = new AffineTransform();
            for(HSLFGroupShape prnt : lst) {
                Rectangle2D exterior = prnt.getAnchor2D();
                Rectangle2D interior = prnt.getCoordinates();

                double scaleX =  exterior.getWidth() / interior.getWidth();
                double scaleY = exterior.getHeight() / interior.getHeight();

                tx.translate(exterior.getX(), exterior.getY());
                tx.scale(scaleX, scaleY);
                tx.translate(-interior.getX(), -interior.getY());
                
            }
            anchor = tx.createTransformedShape(anchor).getBounds2D();
        }

        double angle = getRotation();
        if(angle != 0.){
            double centerX = anchor.getX() + anchor.getWidth()/2;
            double centerY = anchor.getY() + anchor.getHeight()/2;

            AffineTransform trans = new AffineTransform();
            trans.translate(centerX, centerY);
            trans.rotate(Math.toRadians(angle));
            trans.translate(-centerX, -centerY);

            Rectangle2D rect = trans.createTransformedShape(anchor).getBounds2D();
            if((anchor.getWidth() < anchor.getHeight() && rect.getWidth() > rect.getHeight()) ||
                (anchor.getWidth() > anchor.getHeight() && rect.getWidth() < rect.getHeight())    ){
                trans = new AffineTransform();
                trans.translate(centerX, centerY);
                trans.rotate(Math.PI/2);
                trans.translate(-centerX, -centerY);
                anchor = trans.createTransformedShape(anchor).getBounds2D();
            }
        }
        return anchor;
    }

    /**
     *  Find a record in the underlying EscherClientDataRecord
     *
     * @param recordType type of the record to search
     */
    @SuppressWarnings("unchecked")
    protected <T extends Record> T getClientDataRecord(int recordType) {

        Record[] records = getClientRecords();
        if(records != null) for (int i = 0; i < records.length; i++) {
            if(records[i].getRecordType() == recordType){
                return (T)records[i];
            }
        }
        return null;
    }

    /**
     * Search for EscherClientDataRecord, if found, convert its contents into an array of HSLF records
     *
     * @return an array of HSLF records contained in the shape's EscherClientDataRecord or <code>null</code>
     */
    protected Record[] getClientRecords() {
        if(_clientData == null){
            EscherRecord r = getEscherChild(EscherClientDataRecord.RECORD_ID);
            //ddf can return EscherContainerRecord with recordId=EscherClientDataRecord.RECORD_ID
            //convert in to EscherClientDataRecord on the fly
            if(r != null && !(r instanceof EscherClientDataRecord)){
                byte[] data = r.serialize();
                r = new EscherClientDataRecord();
                r.fillFields(data, 0, new DefaultEscherRecordFactory());
            }
            _clientData = (EscherClientDataRecord)r;
        }
        if(_clientData != null && _clientRecords == null){
            byte[] data = _clientData.getRemainingData();
            _clientRecords = Record.findChildRecords(data, 0, data.length);
        }
        return _clientRecords;
    }

    protected void updateClientData() {
        if(_clientData != null && _clientRecords != null){
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                for (int i = 0; i < _clientRecords.length; i++) {
                    _clientRecords[i].writeOut(out);
                }
            } catch(Exception e){
                throw new HSLFException(e);
            }
            _clientData.setRemainingData(out.toByteArray());
        }
    }

    public void setHyperlink(HSLFHyperlink link){
        if(link.getId() == -1){
            throw new HSLFException("You must call SlideShow.addHyperlink(Hyperlink link) first");
        }

        EscherClientDataRecord cldata = new EscherClientDataRecord();
        cldata.setOptions((short)0xF);
        getSpContainer().addChildRecord(cldata); // TODO - junit to prove getChildRecords().add is wrong

        InteractiveInfo info = new InteractiveInfo();
        InteractiveInfoAtom infoAtom = info.getInteractiveInfoAtom();

        switch(link.getType()){
            case HSLFHyperlink.LINK_FIRSTSLIDE:
                infoAtom.setAction(InteractiveInfoAtom.ACTION_JUMP);
                infoAtom.setJump(InteractiveInfoAtom.JUMP_FIRSTSLIDE);
                infoAtom.setHyperlinkType(InteractiveInfoAtom.LINK_FirstSlide);
                break;
            case HSLFHyperlink.LINK_LASTSLIDE:
                infoAtom.setAction(InteractiveInfoAtom.ACTION_JUMP);
                infoAtom.setJump(InteractiveInfoAtom.JUMP_LASTSLIDE);
                infoAtom.setHyperlinkType(InteractiveInfoAtom.LINK_LastSlide);
                break;
            case HSLFHyperlink.LINK_NEXTSLIDE:
                infoAtom.setAction(InteractiveInfoAtom.ACTION_JUMP);
                infoAtom.setJump(InteractiveInfoAtom.JUMP_NEXTSLIDE);
                infoAtom.setHyperlinkType(InteractiveInfoAtom.LINK_NextSlide);
                break;
            case HSLFHyperlink.LINK_PREVIOUSSLIDE:
                infoAtom.setAction(InteractiveInfoAtom.ACTION_JUMP);
                infoAtom.setJump(InteractiveInfoAtom.JUMP_PREVIOUSSLIDE);
                infoAtom.setHyperlinkType(InteractiveInfoAtom.LINK_PreviousSlide);
                break;
            case HSLFHyperlink.LINK_URL:
                infoAtom.setAction(InteractiveInfoAtom.ACTION_HYPERLINK);
                infoAtom.setJump(InteractiveInfoAtom.JUMP_NONE);
                infoAtom.setHyperlinkType(InteractiveInfoAtom.LINK_Url);
                break;
            case HSLFHyperlink.LINK_SLIDENUMBER:
                infoAtom.setAction(InteractiveInfoAtom.ACTION_HYPERLINK);
                infoAtom.setJump(InteractiveInfoAtom.JUMP_NONE);
                infoAtom.setHyperlinkType(InteractiveInfoAtom.LINK_SlideNumber);
                break;
        }

        infoAtom.setHyperlinkID(link.getId());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            info.writeOut(out);
        } catch(Exception e){
            throw new HSLFException(e);
        }
        cldata.setRemainingData(out.toByteArray());

    }

    public Guide getAdjustValue(String name) {
        if (name == null || !name.matches("adj([1-9]|10)")) {
            throw new IllegalArgumentException("Adjust value '"+name+"' not supported.");
        }
        short escherProp;
        switch (Integer.parseInt(name.substring(3))) {
            case 1: escherProp = EscherProperties.GEOMETRY__ADJUSTVALUE; break;
            case 2: escherProp = EscherProperties.GEOMETRY__ADJUST2VALUE; break;
            case 3: escherProp = EscherProperties.GEOMETRY__ADJUST3VALUE; break;
            case 4: escherProp = EscherProperties.GEOMETRY__ADJUST4VALUE; break;
            case 5: escherProp = EscherProperties.GEOMETRY__ADJUST5VALUE; break;
            case 6: escherProp = EscherProperties.GEOMETRY__ADJUST6VALUE; break;
            case 7: escherProp = EscherProperties.GEOMETRY__ADJUST7VALUE; break;
            case 8: escherProp = EscherProperties.GEOMETRY__ADJUST8VALUE; break;
            case 9: escherProp = EscherProperties.GEOMETRY__ADJUST9VALUE; break;
            case 10: escherProp = EscherProperties.GEOMETRY__ADJUST10VALUE; break;
            default: throw new RuntimeException();
        }
        
        int adjval = getEscherProperty(escherProp, -1);
        return (adjval == -1) ? null : new Guide(name, "val "+adjval);
    }

    public LineDecoration getLineDecoration() {
        // TODO Auto-generated method stub
        return null;
    }

    public CustomGeometry getGeometry() {
        ShapeType st = getShapeType();
        String name = st.getOoxmlName();
        
        PresetGeometries dict = PresetGeometries.getInstance();
        CustomGeometry geom = dict.get(name);
        if(geom == null) {
            throw new IllegalStateException("Unknown shape geometry: " + name);
        }
        
        return geom;
    }

    public Shadow getShadow() {
        // TODO Auto-generated method stub
        return null;
    }

    
    
}