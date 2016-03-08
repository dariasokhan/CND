/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.clas.detector;

import javax.swing.JFrame;
import org.jlab.clas.detector.DetectorDescriptor;
import org.jlab.clas.detector.DetectorType;
import org.jlab.clas12.calib.DetectorShape2D;
import org.jlab.clas12.calib.DetectorShapeTabView;
import org.jlab.clas12.calib.DetectorShapeView2D;
import org.jlab.clas12.calib.IDetectorListener;

/**
 *
 * @author gavalian
 */
public class SimpleDetectorDraw extends JFrame implements IDetectorListener {
    
    DetectorShapeTabView  view = new DetectorShapeTabView();
    
    public SimpleDetectorDraw(){
        super();
        initDetector();
        this.setSize(500, 500);
        this.add(view);
        this.pack();
        this.setVisible(true);
    }
    
    private void initDetector(){
        DetectorShapeView2D  dv1 = new DetectorShapeView2D("My Detector 1");
        for(int sector = 0; sector < 6; sector++){
            for(int paddle = 0; paddle < 12; paddle++){
                DetectorShape2D  shape = new DetectorShape2D(DetectorType.FTOF1A,sector,0,paddle);
                // create a trapezoid with (dx1,dx2,dy) 
                shape.createTrapXY(40.0 + 10*paddle, 36.0 + 10*paddle, 6.0);
                shape.getShapePath().translateXYZ( 0.0, 60 + 8*paddle, 0.0);
                shape.getShapePath().rotateZ(Math.toRadians(sector*60.0));
                if(paddle%2==0){
                    shape.setColor(180, 255, 180);
                } else {
                    shape.setColor(180, 180, 255);
                }
                dv1.addShape(shape);
                
            }
        }
        this.view.addDetectorLayer(dv1);
        
        DetectorShapeView2D  dv2 = new DetectorShapeView2D("My Detector 2");
        for(int sector = 0; sector < 6; sector++){
            for(int paddle = 0; paddle < 4; paddle++){
                DetectorShape2D  shape = new DetectorShape2D(DetectorType.FTOF2,sector,1,paddle);
                // create an Arc with 
                // inner  radius = 40 + paddle*10
                // outter radius = 50 + paddle*10
                // starting angle -25.0 degrees
                // ending angle    25.0 degrees
                shape.createArc(40 + paddle*10, 40 + paddle*10 + 10, -25.0, 25.0);
                shape.getShapePath().rotateZ(Math.toRadians(sector*60.0));
                if(paddle%2==0){
                    shape.setColor(180, 255, 180);
                } else {
                    shape.setColor(180, 180, 255);
                }
                dv2.addShape(shape);                
            }
        }
        this.view.addDetectorLayer(dv2);
        view.addDetectorListener(this);
    }

    public void detectorSelected(DetectorDescriptor dd) {
        System.out.println("--------->>>>> you clicked on : " 
                + dd.getSector() + "  " + dd.getLayer() + "  " + dd.getComponent());
    }

    public void update(DetectorShape2D dsd) {
        
    }
    
    
    public static void main(String[] args){
        SimpleDetectorDraw  draw = new SimpleDetectorDraw();
    }
}
