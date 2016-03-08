/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.clas.detector;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import org.jlab.clas.detector.DetectorCollection;
import org.jlab.clas.detector.DetectorDescriptor;
import org.jlab.clas.detector.DetectorType;
import org.jlab.clas12.basic.IDetectorProcessor;
import org.jlab.clas12.calib.DetectorShape2D;
import org.jlab.clas12.calib.DetectorShapeTabView;
import org.jlab.clas12.calib.DetectorShapeView2D;
import org.jlab.clas12.calib.IDetectorListener;
import org.jlab.clasrec.main.DetectorEventProcessorDialog;
import org.jlab.data.io.DataEvent;
import org.jlab.evio.clas12.EvioDataBank;
import org.jlab.evio.clas12.EvioDataEvent;
import org.root.attr.ColorPalette;
import org.root.histogram.H1D;
import org.root.pad.EmbeddedCanvas;

/**
 *
 * @author gavalian
 */
public class SimpleDetectorWithCanvas extends JFrame implements IDetectorListener, IDetectorProcessor, ActionListener {

    DetectorCollection<H1D>  tdcH = new DetectorCollection<H1D>();
    DetectorCollection<H1D>  adcH = new DetectorCollection<H1D>();
    
    DetectorShapeTabView  view   = new DetectorShapeTabView();
    EmbeddedCanvas        canvas = new EmbeddedCanvas();
    int                   nProcessed = 0;

    // ColorPalette class defines colors 
    ColorPalette         palette   = new ColorPalette();
    
    public SimpleDetectorWithCanvas(){
        super();
        
        this.initDetector();
        this.initHistograms();
        this.setLayout(new BorderLayout());
        JSplitPane  splitPane = new JSplitPane();
        splitPane.setLeftComponent(this.view);
        splitPane.setRightComponent(this.canvas);
        this.add(splitPane,BorderLayout.CENTER);
        JPanel buttons = new JPanel();
        JButton process = new JButton("Process");
        buttons.setLayout(new FlowLayout());
        buttons.add(process);
        process.addActionListener(this);
        this.add(buttons,BorderLayout.PAGE_END);
        this.pack();
        this.setVisible(true);
    }
    
    
    private void initHistograms(){
        for(int sector = 0; sector < 6; sector++){
            for(int paddle = 0; paddle < 5; paddle++){
                // DetectorDescriptor.getName() returns a numbered 
                // String with sector, layer and paddle numbers.
                tdcH.add(sector, 2, paddle, 
                        new H1D(DetectorDescriptor.getName("TDC", sector,2,paddle),
                        300,0.0,5000.0));
                adcH.add(sector, 2, paddle, 
                        new H1D(DetectorDescriptor.getName("ADC", sector,2,paddle),
                        300,0.0,5000.0));
            }
        }
    }
    /**
     * Creates a detector Shape.
     */
    private void initDetector(){
       
        DetectorShapeView2D  dv2 = new DetectorShapeView2D("My Detector 2");
        for(int sector = 0; sector < 6; sector++){
            for(int paddle = 0; paddle < 5; paddle++){
                DetectorShape2D  shape = new DetectorShape2D(DetectorType.FTOF2,sector,2,paddle);
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
    /**
     * When the detector is clicked, this function is called
     * @param desc 
     */
    public void detectorSelected(DetectorDescriptor desc) {
        this.canvas.divide(1,2);
        if(tdcH.hasEntry(desc.getSector(),desc.getLayer(),desc.getComponent())){
            H1D h1 = tdcH.get(desc.getSector(),desc.getLayer(),desc.getComponent());
            h1.setTitle(h1.getName());
            canvas.cd(0);
            canvas.draw(h1);
        }
        if(adcH.hasEntry(desc.getSector(),desc.getLayer(),desc.getComponent())){
            H1D h1 = adcH.get(desc.getSector(),desc.getLayer(),desc.getComponent());
            h1.setTitle(h1.getName());
            canvas.cd(1);
            canvas.draw(h1);
        }
    }
    
    /**
     * Each redraw of the canvas passes detector shape object to this routine
     * and user can change the color of specific component depending
     * on accupancy or some other criteria.
     * @param shape 
     */
    public void update(DetectorShape2D shape) {
        int sector = shape.getDescriptor().getSector();
        int paddle = shape.getDescriptor().getComponent();
        //shape.setColor(200, 200, 200);
        if(this.tdcH.hasEntry(sector, 2,paddle)){
            int nent = this.tdcH.get(sector, 2,paddle).getEntries();
            Color col = palette.getColor3D(nent, nProcessed, true);
            /*int colorRed = 240;
            if(nProcessed!=0){
                colorRed = (255*nent)/(nProcessed);
            }*/
            shape.setColor(col.getRed(),col.getGreen(),col.getBlue());
        }
    }

    public void processEvent(DataEvent de) {
        
       
        
        EvioDataEvent event = (EvioDataEvent) de;
        if(event.hasBank("FTOF2B::dgtz")){
            EvioDataBank bank = (EvioDataBank) event.getBank("FTOF2B::dgtz");
            int  rows = bank.rows();
            
            for(int row = 0; row < rows; row++){
                this.nProcessed++;
                int sector = bank.getInt("sector", row) - 1;
                int paddle = bank.getInt("paddle", row) - 1;
                int ADCL   = bank.getInt("ADCL", row);
                int TDCL   = bank.getInt("TDCL", row);
                tdcH.get(sector, 2, paddle).fill(TDCL);
                adcH.get(sector, 2, paddle).fill(ADCL);
            }
        }
    }

    public void actionPerformed(ActionEvent e) {
        if(e.getActionCommand().compareTo("Process")==0){
            DetectorEventProcessorDialog dialog = new DetectorEventProcessorDialog(this);
        }
    }
    
    public static void main(String[] args){
        new SimpleDetectorWithCanvas();
    }
}
