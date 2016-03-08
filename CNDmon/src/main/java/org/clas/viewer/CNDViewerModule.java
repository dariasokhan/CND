/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.clas.viewer;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.List;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import org.jlab.clas.detector.DetectorBankEntry;
import org.jlab.clas.detector.DetectorCollection;
import org.jlab.clas.detector.DetectorDescriptor;
import org.jlab.clas.detector.DetectorType;
import org.jlab.clas12.basic.IDetectorModule;
import org.jlab.clas12.basic.IDetectorProcessor;
import org.jlab.clas12.calib.DetectorShape2D;
import org.jlab.clas12.calib.DetectorShapeTabView;
import org.jlab.clas12.calib.DetectorShapeView2D;
import org.jlab.clas12.calib.IDetectorListener;
import org.jlab.clas12.detector.DetectorChannel;
import org.jlab.clas12.detector.DetectorCounter;
import org.jlab.clas12.detector.EventDecoder;
import org.jlab.clas12.detector.FADCBasicFitter;
import org.jlab.clas12.detector.IFADCFitter;
import org.jlab.clasrec.main.DetectorEventProcessorPane;
import org.jlab.data.func.F1D;
import org.jlab.data.io.DataEvent;
import org.jlab.evio.clas12.EvioDataBank;
import org.jlab.evio.clas12.EvioDataEvent;
import org.root.attr.ColorPalette;
import org.root.basic.EmbeddedCanvas;
import org.root.histogram.H1D;

/**
 *
 * @author gavalian
 */
public class CNDViewerModule implements IDetectorProcessor, IDetectorListener, IDetectorModule,  ActionListener {

    DetectorCollection<H1D> H_WAVEL = new DetectorCollection<H1D>();      // individual waveforms
    DetectorCollection<H1D> H_WAVER = new DetectorCollection<H1D>();
    DetectorCollection<H1D> H_fADCL = new DetectorCollection<H1D>();      // for now, waveforms for all read events
    DetectorCollection<H1D> H_fADCR = new DetectorCollection<H1D>();  
    DetectorCollection<H1D> H_ChargeL = new DetectorCollection<H1D>();    // integrated counts under pulse (from waverform)
    DetectorCollection<H1D> H_ChargeR = new DetectorCollection<H1D>();
    DetectorCollection<H1D> H_TDCL = new DetectorCollection<H1D>();       // TDC, for all read events 
    DetectorCollection<H1D> H_TDCR = new DetectorCollection<H1D>();
    DetectorCollection<H1D> H_TDCLclean = new DetectorCollection<H1D>();  // TDC after best hit selection and ref time subtraction 
    DetectorCollection<H1D> H_TDCRclean = new DetectorCollection<H1D>();
    DetectorCollection<H1D> H_TDCL_N = new DetectorCollection<H1D>();     // number of hits per TDC, for all read events
    DetectorCollection<H1D> H_TDCR_N = new DetectorCollection<H1D>();    
    DetectorCollection<H1D> H_TDCdiff = new DetectorCollection<H1D>();    // TDC differences: L - R (equivalent to distance)
    DetectorCollection<H1D> H_TDCsum = new DetectorCollection<H1D>();     // TDC sums: L + R (equivalent to average time of hit
    DetectorCollection<H1D> H_TDCavdiff = new DetectorCollection<H1D>();
    
    H1D H_TDCtrig = null;
    
    DetectorCollection<F1D> mylandau = new DetectorCollection<F1D>();    
    
    int threshold = 12;  // 10 fADC value <-> ~ 5mV   Comment from Raffaella's code. Not used here!
    int ped_i1 = 2;      // range of bin numbers (on waveform) for the pedestal region
    int ped_i2 = 35;
    int pul_i1 = 38;     //  range of bin numbers (on waveform) for the pulse region
    int pul_i2 = 55;
    
    int TDC_method = 2;    // Method 1 uses ch0 trigger TDC as reference time, method 2 chooses the average time from the other two layers as ref time.
    
    DetectorCollection<Integer>     cndHits = new DetectorCollection<Integer>();
    
    // Set-up for the viewer screen:
    JPanel  detectorPanel = null;
    DetectorShapeTabView  view   = new DetectorShapeTabView();
    DetectorEventProcessorPane evPane = new DetectorEventProcessorPane();
    EmbeddedCanvas canvas = new EmbeddedCanvas();
    EventDecoder decoder = new EventDecoder();
     
    ColorPalette palette = new ColorPalette();  // defines colours
    //ColorPalette.setColor(4,255,215,0); // will change the default color indexed 4 to your desired color. Doesn't work!
   
    int nProcessed = 0;
    
    private int plotSelect = 0;  
    private int keySelect = 0;       // gets assigned by clicking -- selects paddle pair
    private int keySelectlayer = 0;  // gets assigned by clicking -- selects layer
    
    
    public CNDViewerModule(){   // this is the main function
        
        this.initDetector();
        this.initHistograms();
        this.initRawDataDecoder();
        

        // split panel:
        JSplitPane splitPane = new JSplitPane();
        
        // fill CND tab with detector view and canvas
        splitPane.setLeftComponent(this.view);
        JPanel canvasPane = new JPanel();
        canvasPane.setLayout(new BorderLayout());
        JPanel buttonPane = new JPanel();
        buttonPane.setLayout(new FlowLayout());

        // Create a bunch of buttons here to view different things:
        JButton resetBtn = new JButton("Reset");
        resetBtn.addActionListener(this);
        buttonPane.add(resetBtn);

        JButton fitBtn = new JButton("Fit");
        fitBtn.addActionListener(this);
        buttonPane.add(fitBtn);
        
        ButtonGroup group = new ButtonGroup();

        JRadioButton wavesRb  = new JRadioButton("Wave");
        group.add(wavesRb);
        buttonPane.add(wavesRb);
        wavesRb.setSelected(true);
        wavesRb.addActionListener(this);
        
        JRadioButton adcRb  = new JRadioButton("All waves");
        group.add(adcRb);
        buttonPane.add(adcRb);
        adcRb.setSelected(true);
        adcRb.addActionListener(this);
        
        JRadioButton chargeRb  = new JRadioButton("Charge");
        group.add(chargeRb);
        buttonPane.add(chargeRb);
        chargeRb.setSelected(true);
        chargeRb.addActionListener(this);
        
        JRadioButton tdcRb  = new JRadioButton("TDC");
        group.add(tdcRb);
        buttonPane.add(tdcRb);
        tdcRb.setSelected(true);
        tdcRb.addActionListener(this);
        
        JRadioButton tdcstatRb  = new JRadioButton("TDC stats");
        group.add(tdcstatRb);
        buttonPane.add(tdcstatRb);
        tdcstatRb.setSelected(true);
        tdcstatRb.addActionListener(this);
        
        JRadioButton tdcdiffRb  = new JRadioButton("TDC diff/sum");
        group.add(tdcdiffRb);
        buttonPane.add(tdcdiffRb);
        tdcdiffRb.setSelected(true);
        tdcdiffRb.addActionListener(this);
        
        JRadioButton tdcrefRb  = new JRadioButton("TDC ref");
        group.add(tdcrefRb);
        buttonPane.add(tdcrefRb);
        tdcrefRb.setSelected(true);
        tdcrefRb.addActionListener(this);
        
        JRadioButton tdccleanRb  = new JRadioButton("TDC clean");
        group.add(tdccleanRb);
        buttonPane.add(tdccleanRb);
        tdccleanRb.setSelected(true);
        tdccleanRb.addActionListener(this);
       
        canvasPane.add(this.canvas, BorderLayout.CENTER);
        canvasPane.add(buttonPane, BorderLayout.PAGE_END);
        splitPane.setRightComponent(canvasPane);
         
        this.detectorPanel = new JPanel();
        this.detectorPanel.setLayout(new BorderLayout());
        this.evPane.addProcessor(this);
        this.detectorPanel.add(splitPane, BorderLayout.CENTER);
        this.detectorPanel.add(this.evPane, BorderLayout.PAGE_END);
        
    }
    
    private void initDetector(){   // this creates the geometry which is displayed
        
        double phi_slice = 15.;    // degrees corresponding to single pair
        double phi_first = phi_slice/-2. - 90.;  // start of first paddle pair at the top       
        double r_inner = 30.;      // inner radius of inner layer (cm)
        double thickness = 3.;     // thickness of each paddle (cm)
        
        double radius_in = 0.;
        double radius_out = 0.;
        double phi_start = 0.;
        double phi_end = 0.;
        
        DetectorShapeView2D viewCND = new DetectorShapeView2D("CND");
        
        for (int layer = 0; layer < 3; layer++) {
        
            radius_in = r_inner + layer*thickness;
            radius_out = radius_in + thickness;
            
            for (int pair = 0; pair < 24; pair++) {
            
                phi_start = phi_first + phi_slice * pair;
                phi_end = phi_start + phi_slice;
                
                DetectorShape2D shape = new DetectorShape2D(DetectorType.CND, 1, layer, pair);  // numbering from 1
                shape.createArc(radius_in,radius_out,phi_start,phi_end);
                
                viewCND.addShape(shape);
            }
        }
        
        this.view.addDetectorLayer(viewCND);
        view.addDetectorListener(this);
    }
   
    private void initRawDataDecoder() {
        decoder.addFitter(DetectorType.CND,
                new FADCBasicFitter(ped_i1, // first bin for pedestal
                        ped_i2, // last bin for pedestal
                        pul_i1, // first bin for pulse integral
                        pul_i2 // last bin for pulse integral
                ));
    }
    
    private void initHistograms() {
        
        // For the raw TDC plots:
        double TDCx1 = -10000.;    // to display TDC - trigger ref time
        double TDCx2 = 0.;
        int TDCbins = 5000;
        //double TDCx1 = 93000.;   // to display just TDCs
        //double TDCx2 = 96000.;
        //int TDCbins = 1500;
        
        // For the "clean" TDC plots:
        double TDCclx1 = 0.;
        double TDCclx2 = 0.;
        int TDCclbins = 0;
        
        if (TDC_method == 1){
            TDCclx1 = -9600.;    // using ch0 trigger as refernce
            TDCclx2 = -8800.;
            TDCclbins = 800; 
        }
        else if (TDC_method == 2){
            TDCclx1 = -300.;       // using average time from other two layers as reference
            TDCclx2 = 300.;
            TDCclbins = 300;
        }
        
           
        for (int layer = 0; layer < 3; layer++) {
            for (int pair = 0; pair < 24; pair++) {
 
                String titlebase = "Layer " + layer + ", pair " + pair;

                String title = titlebase + ": fADC L";
                H_WAVEL.add(1, layer, pair, new H1D(DetectorDescriptor.getName("WAVEL", 1, layer, pair), title, 100, 0.0, 100.0));
                H_fADCL.add(1, layer, pair, new H1D(DetectorDescriptor.getName("fADCL", 1, layer, pair), title, 100, 0.0, 100.0));
                H_ChargeL.add(1, layer, pair, new H1D(DetectorDescriptor.getName("ChargeL", 1, layer, pair), title, 600, 0.0, 6000.0));
                
                title = titlebase + ": fADC R";
                H_WAVER.add(1, layer, pair, new H1D(DetectorDescriptor.getName("WAVER", 1, layer, pair), title, 100, 0.0, 100.0));
                H_fADCR.add(1, layer, pair, new H1D(DetectorDescriptor.getName("fADCR", 1, layer, pair), title, 100, 0.0, 100.0));
                H_ChargeR.add(1, layer, pair, new H1D(DetectorDescriptor.getName("ChargeR", 1, layer, pair), title, 600, 0.0, 6000.0));
                
                title = titlebase + ": TDC L";
                H_TDCL.add(1, layer, pair, new H1D(DetectorDescriptor.getName("TDCL", 1, layer, pair), title, TDCbins, TDCx1, TDCx2));
                H_TDCL_N.add(1, layer, pair, new H1D(DetectorDescriptor.getName("TDCR_N", 1, layer, pair), title, 10, 0.0, 10.0));
                
                title = titlebase + ": TDC R";
                H_TDCR.add(1, layer, pair, new H1D(DetectorDescriptor.getName("TDCL", 1, layer, pair), title, TDCbins, TDCx1, TDCx2));
                H_TDCR_N.add(1, layer, pair, new H1D(DetectorDescriptor.getName("TDCR_N", 1, layer, pair), title, 10, 0.0, 10.0));
                
                title = titlebase + ": TDC L-R difference";
                H_TDCdiff.add(1, layer, pair, new H1D(DetectorDescriptor.getName("TDCdiff", 1, layer, pair), title, 1200, -600.0, 600.0));
                
                title = titlebase + ": TDC L+R sum";
                H_TDCsum.add(1, layer, pair, new H1D(DetectorDescriptor.getName("TDCsum", 1, layer, pair), title, 200, -200.0, 200.0));
                
                title = titlebase + ": TDC L best hit, minus ref time";
                H_TDCLclean.add(1, layer, pair, new H1D(DetectorDescriptor.getName("TDCLclean", 1, layer, pair), title, TDCclbins, TDCclx1, TDCclx2));
                
                title = titlebase + ": TDC R best hit, minus ref time";
                H_TDCRclean.add(1, layer, pair, new H1D(DetectorDescriptor.getName("TDCRclean", 1, layer, pair), title, TDCclbins, TDCclx1, TDCclx2));
                
                title = titlebase + ": TDC average time diff between layers";
                H_TDCavdiff.add(1, layer, pair, new H1D(DetectorDescriptor.getName("TDCavdiff", 1, layer, pair), title, 5000, -10000., 10000.));
                
                H_WAVEL.get(1, layer, pair).setFillColor(6);
                H_WAVEL.get(1, layer, pair).setXTitle("ADC L waveform");
                H_WAVEL.get(1, layer, pair).setYTitle("counts");
                
                H_WAVER.get(1, layer, pair).setFillColor(6);
                H_WAVER.get(1, layer, pair).setXTitle("ADC R waveform");
                H_WAVER.get(1, layer, pair).setYTitle("counts");
                
                H_fADCL.get(1, layer, pair).setFillColor(6);
                H_fADCL.get(1, layer, pair).setXTitle("ADC L waveforms (all events, pedestal subtracted)");
                H_fADCL.get(1, layer, pair).setYTitle("counts");
                
                H_fADCR.get(1, layer, pair).setFillColor(6);
                H_fADCR.get(1, layer, pair).setXTitle("ADC R waveforms (all events, pedestal subtracted)");
                H_fADCR.get(1, layer, pair).setYTitle("counts");
                
                H_TDCL.get(1, layer, pair).setFillColor(6);
                H_TDCL.get(1, layer, pair).setXTitle("TDC L channels");
                H_TDCL.get(1, layer, pair).setYTitle("counts");
                
                H_TDCR.get(1, layer, pair).setFillColor(6);
                H_TDCR.get(1, layer, pair).setXTitle("TDC R channels");
                H_TDCR.get(1, layer, pair).setYTitle("counts");
                
                H_TDCL_N.get(1, layer, pair).setFillColor(6);
                H_TDCL_N.get(1, layer, pair).setXTitle("TDC L hits per event");
                H_TDCL_N.get(1, layer, pair).setYTitle("counts");
                
                H_TDCR_N.get(1, layer, pair).setFillColor(6);
                H_TDCR_N.get(1, layer, pair).setXTitle("TDC R hits per event");
                H_TDCR_N.get(1, layer, pair).setYTitle("counts");
              
                H_TDCdiff.get(1, layer, pair).setFillColor(6);
                H_TDCdiff.get(1, layer, pair).setXTitle("TDC L - TRDC R");
                H_TDCdiff.get(1, layer, pair).setYTitle("counts");
                
                H_TDCsum.get(1, layer, pair).setFillColor(6);
                H_TDCsum.get(1, layer, pair).setXTitle("TDC L + TRDC R");
                H_TDCsum.get(1, layer, pair).setYTitle("counts");
                
                H_TDCavdiff.get(1, layer, pair).setFillColor(6);
                H_TDCavdiff.get(1, layer, pair).setXTitle("TDC (L+R)/2 - average from other two layers");
                H_TDCavdiff.get(1, layer, pair).setYTitle("counts");
                              
                H_TDCLclean.get(1, layer, pair).setFillColor(6);
                H_TDCLclean.get(1, layer, pair).setXTitle("TDC L channels");
                H_TDCLclean.get(1, layer, pair).setYTitle("counts");
                
                H_TDCRclean.get(1, layer, pair).setFillColor(6);
                H_TDCRclean.get(1, layer, pair).setXTitle("TDC R channels");
                H_TDCRclean.get(1, layer, pair).setYTitle("counts");
                
                H_ChargeL.get(1, layer, pair).setFillColor(6);
                H_ChargeL.get(1, layer, pair).setXTitle("Integrated wave (pedestal-subtracted), L");
                H_ChargeL.get(1, layer, pair).setYTitle("counts");
                
                H_ChargeR.get(1, layer, pair).setFillColor(6);
                H_ChargeR.get(1, layer, pair).setXTitle("Integrated wave (pedestal-subtracted), R");
                H_ChargeR.get(1, layer, pair).setYTitle("counts");
    
                mylandau.add(1, layer, pair, new F1D("landau", 0.0, 80.0));
                
            }
        }
        
       H_TDCtrig = new H1D("TDCtrig", "TDC trigger ref", 5000, 100000., 110000.);
       H_TDCtrig.setFillColor(6);
       H_TDCtrig.setXTitle("TDC trig ref");
       H_TDCtrig.setYTitle("counts");
        
    }
    
    private void resetHistograms() {                       // pressing the reset button should do this
        for (int layer = 0; layer < 3; layer++) {
            for (int pair = 0; pair < 24; pair++) {
                H_WAVEL.get(1, layer, pair).reset();
                H_WAVER.get(1, layer, pair).reset();
                H_fADCL.get(1, layer, pair).reset();
                H_fADCR.get(1, layer, pair).reset();
                H_TDCL.get(1, layer, pair).reset();
                H_TDCR.get(1, layer, pair).reset();
                H_TDCL_N.get(1, layer, pair).reset();
                H_TDCR_N.get(1, layer, pair).reset();
                H_ChargeL.get(1, layer, pair).reset();
                H_ChargeR.get(1, layer, pair).reset();
                H_TDCdiff.get(1, layer, pair).reset();
                H_TDCsum.get(1, layer, pair).reset();
                H_TDCLclean.get(1, layer, pair).reset();
                H_TDCRclean.get(1, layer, pair).reset();
            }
        }
    }
    
    public void processEvent(DataEvent de) {
        EvioDataEvent event = (EvioDataEvent) de;
        
        decoder.decode(event);
        nProcessed++;
        //System.out.println("event #: " + nProcessed);
        
        List<DetectorBankEntry> entries =  decoder.getDataEntries();
        //for(DetectorBankEntry entry : entries){
        //    System.out.println(entry);
        //}
        
        List<DetectorCounter> counters = decoder.getDetectorCounters(DetectorType.CND);
        CNDViewerModule.MyADCFitter fadcFitter = new CNDViewerModule.MyADCFitter();
        
        double tdc_ref = 0.;   // reference time for the trigger
        
        List<Integer> TDCL1 = null;      // arrays of TDC hits in each event
        List<Integer> TDCR1 = null;
        List<Integer> TDCL2 = null;
        List<Integer> TDCR2 = null;
        List<Integer> TDCL3 = null;
        List<Integer> TDCR3 = null;
        
        int ntdcL[] = {0,0,0};         // number of hits in each TDC in each event
        int ntdcR[] = {0,0,0};
        
        
        // First, get the TDC info from the whole block, for the whole event:
        
        for (DetectorCounter counter : counters) {
            
            int layer = counter.getDescriptor().getLayer();
            int pair = counter.getDescriptor().getComponent();
            
            //System.out.println(layer);
            //System.out.println(pair);
            
            if (pair == 0){   // pair 0 was set up to read ch0: the trigger time
                //System.out.println(counter.getChannels().get(0).getTDC().get(0));
                tdc_ref = counter.getChannels().get(0).getTDC().get(0);    // only one hit observed in this channel, thankfully
                H_TDCtrig.fill(tdc_ref);
            }
            else { // real data               
                // Left:           
                ntdcL[pair-1] = counter.getChannels().get(0).getTDC().size();    // number of hits in the left TDC
                H_TDCL_N.get(1, layer, pair).fill(ntdcL[pair-1]);
                
                if (pair == 1) TDCL1 = counter.getChannels().get(0).getTDC();        // assign TDC values to the correct array
                else if (pair == 2) TDCL2 = counter.getChannels().get(0).getTDC();
                else if (pair == 3) TDCL3 = counter.getChannels().get(0).getTDC();
                
                for (int i = 0; i < ntdcL[pair-1]; i++){
                    H_TDCL.get(1, layer, pair).fill(counter.getChannels().get(0).getTDC().get(i) - tdc_ref);  // fill with all the TDC values
                    //System.out.println(TDCL1.get(i));
                }
                
                // Right:            
                ntdcR[pair-1] = counter.getChannels().get(1).getTDC().size();    // number of hits in the right TDC
                H_TDCR_N.get(1, layer, pair).fill(ntdcR[pair-1]);
                
                if (pair == 1) TDCR1 = counter.getChannels().get(1).getTDC();     
                else if (pair == 2) TDCR2 = counter.getChannels().get(1).getTDC();
                else if (pair == 3) TDCR3 = counter.getChannels().get(1).getTDC();
                
                for (int i = 0; i < ntdcR[pair-1]; i++){
                    H_TDCR.get(1, layer, pair).fill(counter.getChannels().get(1).getTDC().get(i) - tdc_ref);  // fill with all the TDC values
                    //System.out.println(TDCR1.get(i));
                }
            }
            
          
        }
        
        
        // Now, determine the best hits in the TDC!
        
        double TDCgood[] = {0.,0.,0.,0.,0.,0.};   // will hold the best TDC hit values for each paddle (L1, R1, L2, R2, L3, R3)
        int flag_goodevent = 0;                   // determines whether just one possible "good hit" reconstruction for the event.   
        int flag_good = 0;                        // flag to check best hit selection for each paddle
    
        // TWO methods for determining the best TDC hits. The method is set at the start of the code.
        
        /*****************************/
        // METHOD 1
        // Select the "best" hit from each TDC based on trigger reference timing. Should turn the whole thing into arrays!!
        
        if (TDC_method == 1){
            
            int flag_best = 0;  // overall flag to decide whether event has just one good TDC hit for each PMT. 
            
            flag_good = 0;
            for (int i = 0; i < ntdcL[0]; i++){
                if ((TDCL1.get(i) - tdc_ref) > -10000 && (TDCL1.get(i) - tdc_ref) < -8800){
                    flag_good++;
                    TDCgood[0] = TDCL1.get(i);
                }
            }
            if (flag_good == 1) flag_best++;   // require only one hit to be in the "good" time-window
            
            flag_good = 0;
            for (int i = 0; i < ntdcL[1]; i++){
                if ((TDCL2.get(i) - tdc_ref) > -10000 && (TDCL2.get(i) - tdc_ref) < -8800){
                    flag_good++;
                    TDCgood[2] = TDCL2.get(i);
                }
            }
            if (flag_good == 1) flag_best++;   // require only one hit to be in the "good" time-window
            
            flag_good = 0;
            for (int i = 0; i < ntdcL[2]; i++){
                if ((TDCL3.get(i) - tdc_ref) > -10000 && (TDCL3.get(i) - tdc_ref) < -8800){
                    flag_good++;
                    TDCgood[4] = TDCL3.get(i);
                }
            }
            if (flag_good == 1) flag_best++;   // require only one hit to be in the "good" time-window
            
            flag_good = 0;
            for (int i = 0; i < ntdcR[0]; i++){
                if ((TDCR1.get(i) - tdc_ref) > -10000 && (TDCR1.get(i) - tdc_ref) < -8800){
                    flag_good++;
                    TDCgood[1] = TDCR1.get(i);
                }
            }
            if (flag_good == 1) flag_best++;   // require only one hit to be in the "good" time-window
            
            flag_good = 0;
            for (int i = 0; i < ntdcR[1]; i++){
                if ((TDCR2.get(i) - tdc_ref) > -10000 && (TDCR2.get(i) - tdc_ref) < -8800){
                    flag_good++;
                    TDCgood[3] = TDCR2.get(i);
                }
            }
            if (flag_good == 1) flag_best++;   // require only one hit to be in the "good" time-window
            
            flag_good = 0;
            for (int i = 0; i < ntdcR[2]; i++){
                if ((TDCR3.get(i) - tdc_ref) > -10000 && (TDCR3.get(i) - tdc_ref) < -8800){
                    flag_good++;
                    TDCgood[5] = TDCR3.get(i);
                }
            }
            if (flag_good == 1) flag_best++;   // require only one hit to be in the "good" time-window
            
            if (flag_best == 6) flag_goodevent = 1;  // ie: all six paddles returned just one good TDC hit, so a good reconstruction has been possible
        }
        /*****************************/
        
        /*****************************/
        // METHOD 2:
        // This loops through ALL possible combinations of TDC hits and finds difference between average time from one layer
        // and average time from the other two layers combined.
        // The best hit combination is then selected where the difference between average times falls into the tallest peak.
        // Using layer 2 (TDC ch 3 & 4), the peak looks fairly clean, so use that layer to determine best hit combinations.
        
        else if (TDC_method == 2){
            
            double TDC_diff = 0.;
            flag_goodevent = 0;
            
            // Using Layer 1 as a reference, loop through all hits and find differences between average times:
            
            for (int i = 0; i < ntdcL[0]; i++){
                for (int j = 0; j < ntdcR[0]; j++){
                    for (int a1 = 0; a1 < ntdcL[1]; a1++){
                        for (int a2 = 0; a2 < ntdcR[1]; a2++){
                            for (int b1 = 0; b1 < ntdcL[2]; b1++){
                                for (int b2 = 0; b2 < ntdcR[2]; b2++){
                                    
                                    TDC_diff = (TDCL1.get(i)+TDCR1.get(j))/2. - ((TDCL2.get(a1)+TDCR2.get(a2)+TDCL3.get(b1)+TDCR3.get(b2))/4.);
                                    
                                    H_TDCavdiff.get(1, 0, 1).fill(TDC_diff);
                                    
                                }
                            }
                        }
                    }
                }
            }
            
            // Using Layer 2 as a reference, loop through all hits and find differences between average times:
            
            flag_good = 0;
            
            for (int i = 0; i < ntdcL[1]; i++){
                for (int j = 0; j < ntdcR[1]; j++){
                    for (int a1 = 0; a1 < ntdcL[0]; a1++){
                        for (int a2 = 0; a2 < ntdcR[0]; a2++){
                            for (int b1 = 0; b1 < ntdcL[2]; b1++){
                                for (int b2 = 0; b2 < ntdcR[2]; b2++){
                                    
                                    TDC_diff = (TDCL2.get(i)+TDCR2.get(j))/2. - ((TDCL1.get(a1)+TDCR1.get(a2)+TDCL3.get(b1)+TDCR3.get(b2))/4.);
                                    
                                    H_TDCavdiff.get(1, 0, 2).fill(TDC_diff);
                                    
                                    if (TDC_diff > -40. && TDC_diff < 20.){  // select the peak around zero for the "good" hits!
                                        
                                        TDCgood[0] = TDCL1.get(a1);
                                        TDCgood[1] = TDCR1.get(a2);
                                        TDCgood[2] = TDCL2.get(i);
                                        TDCgood[3] = TDCR2.get(j);
                                        TDCgood[4] = TDCL3.get(b1);
                                        TDCgood[5] = TDCR3.get(b2);
                                        
                                        flag_good++;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            if (flag_good == 1) flag_goodevent = 1;   // just one possible combination yielding all good hits!
            
            
            // Using Layer 3 as a reference, loop through all hits and find differences between average times:
            
            for (int i = 0; i < ntdcL[2]; i++){
                for (int j = 0; j < ntdcR[2]; j++){
                    for (int a1 = 0; a1 < ntdcL[0]; a1++){
                        for (int a2 = 0; a2 < ntdcR[0]; a2++){
                            for (int b1 = 0; b1 < ntdcL[1]; b1++){
                                for (int b2 = 0; b2 < ntdcR[1]; b2++){
                                    
                                    TDC_diff = (TDCL3.get(i)+TDCR3.get(j))/2. - ((TDCL1.get(a1)+TDCR1.get(a2)+TDCL2.get(b1)+TDCR2.get(b2))/4.);
                                    
                                    H_TDCavdiff.get(1, 0, 3).fill(TDC_diff);
                                }
                            }
                        }
                    }
                }
            }
        }
        /****************************/        
        
        // Now, fill the "clean" TDC histograms, giving the TDC value minus the reference time:
        
        double TDCclean[] = {0.,0.,0.,0.,0.,0.};   // the reference-adjusted TDC values
         
        if (flag_goodevent == 1){
            
            // Ref time given by average time from the other two layers (better!):
            TDCclean[0] = TDCgood[0]-((TDCgood[2]+TDCgood[3]+TDCgood[4]+TDCgood[5])/4.);
            TDCclean[1] = TDCgood[1]-((TDCgood[2]+TDCgood[3]+TDCgood[4]+TDCgood[5])/4.);
            TDCclean[2] = TDCgood[2]-((TDCgood[0]+TDCgood[1]+TDCgood[4]+TDCgood[5])/4.);
            TDCclean[3] = TDCgood[3]-((TDCgood[0]+TDCgood[1]+TDCgood[4]+TDCgood[5])/4.);
            TDCclean[4] = TDCgood[4]-((TDCgood[0]+TDCgood[1]+TDCgood[2]+TDCgood[3])/4.);
            TDCclean[5] = TDCgood[5]-((TDCgood[0]+TDCgood[1]+TDCgood[2]+TDCgood[3])/4.);
            
            // Ref time given by trigger signal (worse...):
            // for (int i=0; i<6; i++) TDCclean[i] = TDCgood[i] - tdc_ref;
           
            H_TDCLclean.get(1, 0, 1).fill(TDCclean[0]);
            H_TDCRclean.get(1, 0, 1).fill(TDCclean[1]); 
            H_TDCLclean.get(1, 0, 2).fill(TDCclean[2]);
            H_TDCRclean.get(1, 0, 2).fill(TDCclean[3]); 
            H_TDCLclean.get(1, 0, 3).fill(TDCclean[4]);
            H_TDCRclean.get(1, 0, 3).fill(TDCclean[5]); 
            
            
            H_TDCdiff.get(1, 0, 1).fill(TDCgood[0] - TDCgood[1]);
            H_TDCdiff.get(1, 0, 2).fill(TDCgood[2] - TDCgood[3]);
            H_TDCdiff.get(1, 0, 3).fill(TDCgood[4] - TDCgood[5]);
            
            H_TDCsum.get(1, 0, 1).fill(TDCclean[0] + TDCclean[1]);
            H_TDCsum.get(1, 0, 2).fill(TDCclean[2] + TDCclean[3]);
            H_TDCsum.get(1, 0, 3).fill(TDCclean[4] + TDCclean[5]);
        }
         
        
        // Now, fill the ADC / Charge info:
        // Can decide whether you want to do it only for events where there is a "good" hit or all of them. 
        for (DetectorCounter counter : counters) {
                
            int layer = counter.getDescriptor().getLayer();
            int pair = counter.getDescriptor().getComponent();
            
            if (pair != 0) {   // pair 0 carries just the ch0 trigger reference information
                
                // Left:
                short pulseL[] = counter.getChannels().get(0).getPulse();  // gets the left fADC pulse      
                
                H_WAVEL.get(1, layer, pair).reset();    // these get reset for every event
                H_WAVER.get(1, layer, pair).reset();
                
                fadcFitter.fit(counter.getChannels().get(0));   // calculates pedestal and does some other things
                    
                // Fill the waveform histograms:
                for (int i = 0; i < Math.min(pulseL.length, H_fADCL.get(1, layer, pair).getAxis().getNBins()); i++) {
                    H_fADCL.get(1, layer, pair).fill(i, pulseL[i] - fadcFitter.getPedestal() + 10.0);   // offset of 10 is to make any wobbles visible
                    H_WAVEL.get(1, layer, pair).fill(i, pulseL[i]);
                }
                
                // H_ChargeL.get(1, layer, pair).fill(fadcFitter.getIntegratedWave());  // my function. Gagik's does the same, apparently
                H_ChargeL.get(1, layer, pair).fill(counter.getChannels().get(0).getADC().get(0));   // the getADC() function calculates the integral under the pulse
                
                
                // Right:
                short pulseR[] = counter.getChannels().get(1).getPulse();
                
                fadcFitter.fit(counter.getChannels().get(1));   // now, for the right channel -- calculates pedestal and does some other things
                
                // Fill the waveform histograms:
                for (int i = 0; i < Math.min(pulseR.length, H_fADCR.get(1, layer, pair).getAxis().getNBins()); i++) {
                    H_fADCR.get(1, layer, pair).fill(i, pulseR[i] - fadcFitter.getPedestal() + 10.0);   // offset of 10 is to make any wobbles visible
                    H_WAVER.get(1, layer, pair).fill(i, pulseR[i]);
                }
                
                // H_ChargeR.get(1, layer, pair).fill(fadcFitter.getIntegratedWave());  // my function. Gagik's does the same apparently.
                H_ChargeR.get(1, layer, pair).fill(counter.getChannels().get(1).getADC().get(0));   // the getADC() function calculates the integral under the pulse
                
            }  // closes loop over pair == 0
            
            // This decides what to draw, depending on what has been clicked:
            if (plotSelect == 0) {
                this.canvas.divide(1, 2);
                if (H_WAVEL.hasEntry(1, keySelectlayer, keySelect)) {
                    this.canvas.cd(0);
                    this.canvas.draw(H_WAVEL.get(1, keySelectlayer, keySelect));
                } 
                if (H_WAVER.hasEntry(1, keySelectlayer, keySelect)) {
                    this.canvas.cd(1);
                    this.canvas.draw(H_WAVER.get(1, keySelectlayer, keySelect));
                }
            }   
        }  // closes loop over counters. 
             
        this.view.repaint();       
    }
        
    public void detectorSelected(DetectorDescriptor dd) {
       // System.out.println("SELECTED = " + dd);
        
        keySelect = dd.getComponent();
        keySelectlayer = dd.getLayer();
        if (plotSelect == 0) {
            this.canvas.divide(1, 2);
            this.canvas.cd(0);
            this.canvas.setGridX(false);
            this.canvas.setGridY(false);
            this.canvas.setAxisFontSize(10);
            this.canvas.setTitleFontSize(16);
            this.canvas.setAxisTitleFontSize(14);
            this.canvas.setStatBoxFontSize(8);
            this.canvas.draw(H_WAVEL.get(1, keySelectlayer, keySelect),"S");
            this.canvas.cd(1);
            this.canvas.setGridX(false);
            this.canvas.setGridY(false);
            this.canvas.setAxisFontSize(10);
            this.canvas.setTitleFontSize(16);
            this.canvas.setAxisTitleFontSize(14);
            this.canvas.setStatBoxFontSize(8);
            this.canvas.draw(H_WAVER.get(1, keySelectlayer, keySelect),"S");
        }
        else if (plotSelect == 1) {
            this.canvas.divide(1, 2);
            this.canvas.cd(0);
            this.canvas.setGridX(false);
            this.canvas.setGridY(false);
            this.canvas.setAxisFontSize(10);
            this.canvas.setTitleFontSize(16);
            this.canvas.setAxisTitleFontSize(14);
            this.canvas.setStatBoxFontSize(8);
            this.canvas.draw(H_fADCL.get(1, keySelectlayer, keySelect),"S");
            this.canvas.cd(1);
            this.canvas.setGridX(false);
            this.canvas.setGridY(false);
            this.canvas.setAxisFontSize(10);
            this.canvas.setTitleFontSize(16);
            this.canvas.setAxisTitleFontSize(14);
            this.canvas.setStatBoxFontSize(8);
            this.canvas.draw(H_fADCR.get(1, keySelectlayer, keySelect),"S");
        } 
        else if (plotSelect == 2) {
            this.canvas.divide(1, 2);
            this.canvas.cd(0);
            this.canvas.setGridX(false);
            this.canvas.setGridY(false);
            this.canvas.setAxisFontSize(10);
            this.canvas.setTitleFontSize(16);
            this.canvas.setAxisTitleFontSize(14);
            this.canvas.setStatBoxFontSize(8);
            this.canvas.draw(H_ChargeL.get(1, keySelectlayer, keySelect),"S");
            this.canvas.cd(1);
            this.canvas.setGridX(false);
            this.canvas.setGridY(false);
            this.canvas.setAxisFontSize(10);
            this.canvas.setTitleFontSize(16);
            this.canvas.setAxisTitleFontSize(14);
            this.canvas.setStatBoxFontSize(8);
            this.canvas.draw(H_ChargeR.get(1, keySelectlayer, keySelect),"S");
        }
        else if (plotSelect == 3) {   
            this.canvas.divide(1, 2);
            this.canvas.cd(0);
            this.canvas.setGridX(false);
            this.canvas.setGridY(false);
            this.canvas.setAxisFontSize(10);
            this.canvas.setTitleFontSize(16);
            this.canvas.setAxisTitleFontSize(14);
            this.canvas.setStatBoxFontSize(8);
            if (keySelect == 0) this.canvas.draw(H_TDCtrig,"S");   // draw the trig ref time for pair = 0 component
            else this.canvas.draw(H_TDCL.get(1, keySelectlayer, keySelect),"S");
            this.canvas.cd(1);
            this.canvas.setGridX(false);
            this.canvas.setGridY(false);
            this.canvas.setAxisFontSize(10);
            this.canvas.setTitleFontSize(16);
            this.canvas.setAxisTitleFontSize(14);
            this.canvas.setStatBoxFontSize(8);
            this.canvas.draw(H_TDCR.get(1, keySelectlayer, keySelect),"S");
        }
        else if (plotSelect == 4) {
            this.canvas.divide(1, 2);
            canvas.cd(0);
            this.canvas.setGridX(false);
            this.canvas.setGridY(false);
            this.canvas.setAxisFontSize(10);
            this.canvas.setTitleFontSize(16);
            this.canvas.setAxisTitleFontSize(14);
            this.canvas.setStatBoxFontSize(8);
            this.canvas.draw(H_TDCL_N.get(1, keySelectlayer, keySelect),"S");
            canvas.cd(1);
            this.canvas.setGridX(false);
            this.canvas.setGridY(false);
            this.canvas.setAxisFontSize(10);
            this.canvas.setTitleFontSize(16);
            this.canvas.setAxisTitleFontSize(14);
            this.canvas.setStatBoxFontSize(8);
            this.canvas.draw(H_TDCR_N.get(1, keySelectlayer, keySelect),"S");
        }
         else if (plotSelect == 5) {
            this.canvas.divide(1, 2);
            this.canvas.cd(0);
            this.canvas.setGridX(false);
            this.canvas.setGridY(false);
            this.canvas.setAxisFontSize(10);
            this.canvas.setTitleFontSize(16);
            this.canvas.setAxisTitleFontSize(14);
            this.canvas.setStatBoxFontSize(8);
            this.canvas.draw(H_TDCdiff.get(1, keySelectlayer, keySelect),"S");
            this.canvas.cd(1);
            this.canvas.setGridX(false);
            this.canvas.setGridY(false);
            this.canvas.setAxisFontSize(10);
            this.canvas.setTitleFontSize(16);
            this.canvas.setAxisTitleFontSize(14);
            this.canvas.setStatBoxFontSize(8);
            this.canvas.draw(H_TDCsum.get(1, keySelectlayer, keySelect),"S");
        }
        else if (plotSelect == 6) {
            this.canvas.divide(1, 2);
            this.canvas.cd(0);
            this.canvas.setGridX(false);
            this.canvas.setGridY(false);
            this.canvas.setAxisFontSize(10);
            this.canvas.setTitleFontSize(16);
            this.canvas.setAxisTitleFontSize(14);
            this.canvas.setStatBoxFontSize(8);
            this.canvas.draw(H_TDCLclean.get(1, keySelectlayer, keySelect),"S");
            this.canvas.cd(1);
            this.canvas.setGridX(false);
            this.canvas.setGridY(false);
            this.canvas.setAxisFontSize(10);
            this.canvas.setTitleFontSize(16);
            this.canvas.setAxisTitleFontSize(14);
            this.canvas.setStatBoxFontSize(8);
            this.canvas.draw(H_TDCRclean.get(1, keySelectlayer, keySelect),"S");
        }
        else if (plotSelect == 7) {
            this.canvas.divide(1, 2);
            this.canvas.cd(0);
            this.canvas.setGridX(false);
            this.canvas.setGridY(false);
            this.canvas.setAxisFontSize(10);
            this.canvas.setTitleFontSize(16);
            this.canvas.setAxisTitleFontSize(14);
            this.canvas.setStatBoxFontSize(8);
            this.canvas.draw(H_TDCavdiff.get(1, keySelectlayer, keySelect),"S");
        }
        
    }
    
    
    public void update(DetectorShape2D shape) {
        if(this.cndHits.hasEntry(shape.getDescriptor().getSector(), 
                shape.getDescriptor().getLayer(),shape.getDescriptor().getComponent())==true){
            shape.setColor(255, 180, 180);
        } else {
            //if(shape.getDescriptor().getLayer() == 1) shape.setColor(51, 153, 255);
               // else if (shape.getDescriptor().getLayer() == 0) shape.setColor(255, 255, 0);
              //  else if (shape.getDescriptor().getLayer() == 2) shape.setColor(51, 153, 255);
              shape.setColor(180, 255,180);
        }
    }

    public String getName() {
        return "CNDViewrModule";
    }

    public String getAuthor() {
        return "sokhan";
    }

    public DetectorType getType() {
        return DetectorType.CND;
    }

    public String getDescription() {
        return "CND Display";
    }

    public JPanel getDetectorPanel() {
        return this.detectorPanel;
    }
    
    public static void main(String[] args){
        CNDViewerModule module = new CNDViewerModule();
        JFrame frame = new JFrame();
        frame.add(module.getDetectorPanel());
        frame.pack();
        frame.setVisible(true);
    }

    public void actionPerformed(ActionEvent e) {
       
        //System.out.println("ACTION = " + e.getActionCommand());
        if (e.getActionCommand().compareTo("Reset") == 0) {
            resetHistograms();
        }
       // if (e.getActionCommand().compareTo("Fit") == 0) {
       //      fitHistograms();
       //  }
        if (e.getActionCommand().compareTo("Wave") == 0) {
            plotSelect = 0;
            resetCanvas();
        } else if (e.getActionCommand().compareTo("All waves") == 0) {
            plotSelect = 1;
        } else if (e.getActionCommand().compareTo("Charge") == 0) {
            plotSelect = 2;
        } else if (e.getActionCommand().compareTo("TDC") == 0) {
            plotSelect = 3;
        } else if (e.getActionCommand().compareTo("TDC stats") == 0) {
            plotSelect = 4;
        } else if (e.getActionCommand().compareTo("TDC diff/sum") == 0) {
            plotSelect = 5;
        } else if (e.getActionCommand().compareTo("TDC clean") == 0) {
            plotSelect = 6;
        } else if (e.getActionCommand().compareTo("TDC ref") == 0) {
            plotSelect = 7;
        }
    }
    
    private void resetCanvas() {
        this.canvas.divide(1, 2);
        canvas.cd(0);
    }
      
    public class MyADCFitter implements IFADCFitter {

        double rms = 0;
        double pedestal = 0;
        double charge = 0.;
        double wave_max=0;

        public double getPedestal() {
            return pedestal;
        }

        public double getRMS() {
            return rms;
        }

        public double getWave_Max() {
            return wave_max;
        }
        
        public double getIntegratedWave() {
            return charge;
        }
                
        public void fit(DetectorChannel dc) {
            short[] pulse = dc.getPulse();
            double ped = 0.0;
            double waveint = 0.;
            double noise = 0;
            double wmax=0;
            for (int bin = ped_i1; bin < ped_i2; bin++) {
                ped += pulse[bin];
                noise += pulse[bin] * pulse[bin];
            }
            for (int bin=0; bin<pulse.length; bin++) {
                if(pulse[bin]>wmax) wmax=pulse[bin];
            }
            pedestal = ped / (ped_i2 - ped_i1);
            
            for (int bin = pul_i1; bin < pul_i2; bin++) {
                waveint += (pulse[bin] - pedestal);
            }
            charge = waveint;
            
            rms = Math.sqrt(noise / (ped_i2 - ped_i1) - pedestal * pedestal);
            wave_max=wmax;
        }

    }  
}
