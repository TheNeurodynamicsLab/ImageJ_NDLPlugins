/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package NDL_Plugins;

import ij.IJ;
import ij.ImageListener;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.OvalRoi;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.io.RoiDecoder;
import ij.io.RoiEncoder;
import ij.measure.Calibration;
import ij.measure.CurveFitter;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.RoiScaler;
import ij.plugin.frame.RoiManager;
import ij.process.ImageStatistics;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.swing.DefaultListModel;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/**
 *
 * @author Balaji
 */
public class TimeSeries_3D_Analyser_version_2dot0 extends javax.swing.JFrame implements Runnable, MouseListener/*required for add on click*/,ImageListener /*required for knowing if the active image is been closed*/ {
    
    RoiManager Manager;               //Handle to store and access the native ROIManager Instance. 
    ImagePlus currentImp,currSlice;   //Place holders to store and refer the currently active imageplus and the current displayed slice
                                      //in that stack.
    ImageCanvas currCanvas;           //stores the canvas of the currentImp; Used for listening mouseclick events in this canvas. Required for
                                      //implementing add on click feature of this plugin.
    boolean activeImage = false;      //boolean variable to store if there is a current active image. TRUE => there is a actie image;
    //ImageStack currStk;
    
    ArrayList<Roi3D> Rois3D; 
    DefaultListModel<String> Roi3DListModel;
    
    ArrayList<Roi> Rois2D;
    DefaultListModel<String> Roi2DListModel;
   
    boolean done = false;
    ImageCanvas previousCanvas;
    Thread thread;
    
    /**
     * Settings options for autoROI properties are managed in the section bellow
     */
    
    String autoROIPrefix;
    private String roiPrefix = "ROI";
    private String roi3DPrefix = "3D";
    private int start3DRoiNumber = 0;
    private int cur3DRoiNumber = 0;
   // private int cur2DRoiNumber = 0;
    private int roiHeight = 15;
    private int roiWidth = 15;
    private int roiDepth = 10;
    private int roi3DCount = start3DRoiNumber;
    //private int roi2DCount = 0;
    private File defaultPath;
    private double bgd;
    private boolean recentering;

    private void deconstruct3Dto2D(int selIdx) {
        
        if(selIdx != -1){
            Roi [] sel2DRois = Rois3D.get(selIdx).getRoiSet();
            this.btnClearAll2DActionPerformed(null);

            for(Roi roi : sel2DRois){
                addNewRoi(roi);
            }   
        }else{
            btnClearAll2DActionPerformed(null);
            for(Roi3D roi3D : Rois3D ){
                Roi [] roi2DArray = roi3D.getRoiSet();
                for(Roi roi2D : roi2DArray ){
                    addNewRoi(roi2D);
                }
            }
                
        }
    }

    public void recenter(ImagePlus imp, Roi tmpRoi, int z) {
        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        Roi [] rois = new Roi[1];
        rois[0] = tmpRoi;
        recenter(imp,rois,z);
    }

    private void recenter(Roi3D roi3D) {
        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        
        for(Roi roi : roi3D.getRoiSet()){
            recenter(this.currentImp, roi, roi.getPosition());
        }
    }
    enum  roiType{ OVAL,RECTANGLE}; 
    private roiType roiShape = roiType.OVAL;
    
    
    
    /**
     * Creates new form TimeSeries_3D_Analyser_Beta_2dot1
     */
    public TimeSeries_3D_Analyser_version_2dot0() {
        
        if(RoiManager.getInstance() == null){       //No previous instance of Roi Manager; User has not invoked the ROIManager tool yet. 
             Manager = new RoiManager();            //Create a new instance of the RoiManager and obtain a handle to it.
        }else{
             Manager = RoiManager.getInstance();     //Roi MAnager is in use. Get the instance handle and store it for us to use.
        }
       
        currentImp = ij.WindowManager.getCurrentImage(); //Obtain the  currently displayed image in ImageJ If multiple images are open we get the imageplus of the active window.
                                                         //If none of the images are open, the windowmanager returns null and it is stored in currentImp. 
        if(currentImp   != null){
            activeImage = true;                          // The windowmanger of ImageJ returned a non-null value. There is an active image.
        }else{
            currCanvas  = null;                          // The windowmanager returned null => no images open. So no canvas and active image is set to false
            activeImage = false;
        }
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(TimeSeries_3D_Analyser_Beta_2dot1.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            Logger.getLogger(TimeSeries_3D_Analyser_Beta_2dot1.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            Logger.getLogger(TimeSeries_3D_Analyser_Beta_2dot1.class.getName()).log(Level.SEVERE, null, ex);
        } catch (UnsupportedLookAndFeelException ex) {
            Logger.getLogger(TimeSeries_3D_Analyser_Beta_2dot1.class.getName()).log(Level.SEVERE, null, ex);
        }
        initComponents();
        
        Roi2DListModel = new DefaultListModel();
        this.gui2DRoiList.setModel(Roi2DListModel);
        Roi3DListModel = new DefaultListModel();
        this.gui3DRoiList.setModel(Roi3DListModel);
        
        Rois2D = new ArrayList<>();
        Rois3D = new ArrayList<>();
        
        ImagePlus.addImageListener(this);
        
        this.setVisible(true);
        thread = new Thread(this,"Time Series ");
        thread.setPriority(Math.max(thread.getPriority()-2,Thread.MIN_PRIORITY));
        thread.start();
          
        
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        guiSettingsWindow = new javax.swing.JFrame();
        guiSettingsTab = new javax.swing.JTabbedPane();
        guireCtrProperties = new javax.swing.JPanel();
        guiautoROIProperties = new javax.swing.JPanel();
        guiroiPrefix = new javax.swing.JTextField();
        jLabel3 = new javax.swing.JLabel();
        gui3DDepth = new javax.swing.JTextField();
        jLabel4 = new javax.swing.JLabel();
        guiroiWidth = new javax.swing.JTextField();
        guiroiHeight = new javax.swing.JTextField();
        jLabel5 = new javax.swing.JLabel();
        jLabel6 = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();
        guiShape = new javax.swing.JComboBox<>();
        jLabel8 = new javax.swing.JLabel();
        gui3DroiPrefix = new javax.swing.JTextField();
        gui3DRoiStartNumber = new javax.swing.JTextField();
        jLabel9 = new javax.swing.JLabel();
        jLabel10 = new javax.swing.JLabel();
        jLabel11 = new javax.swing.JLabel();
        jCheckBox1 = new javax.swing.JCheckBox();
        jCheckBox2 = new javax.swing.JCheckBox();
        guiSettingsOkBtn = new javax.swing.JButton();
        guiSettingsCancelBtn = new javax.swing.JButton();
        btnGrp_2D_OR_3D_addOnClk = new javax.swing.ButtonGroup();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        mv2Manager = new javax.swing.JButton();
        addto3Dlist = new javax.swing.JButton();
        remove2Dfrom3D = new javax.swing.JButton();
        transferfromManager = new javax.swing.JButton();
        recenterIn2D = new javax.swing.JButton();
        recenterProperties = new javax.swing.JButton();
        buttonExit = new javax.swing.JButton();
        panel_3DBtns_ChkBox = new javax.swing.JPanel();
        AddOnClick = new javax.swing.JCheckBox();
        btnRecenter3D = new javax.swing.JButton();
        btnDefOverlap = new javax.swing.JButton();
        btnMeasure3D = new javax.swing.JButton();
        make3Dbutton = new javax.swing.JButton();
        buttonAutoRoi = new javax.swing.JButton();
        zRecenter = new javax.swing.JButton();
        btnSetBackGround = new javax.swing.JButton();
        btnGenGauInt = new javax.swing.JButton();
        btnDetOverlap = new javax.swing.JButton();
        btnSetMeasurements = new javax.swing.JButton();
        add3D_rad_btn = new javax.swing.JRadioButton();
        add2D_rad_btn = new javax.swing.JRadioButton();
        btnSave3DRois = new javax.swing.JButton();
        btnOpen3DRois = new javax.swing.JButton();
        jSep_ChkBox_Btn = new javax.swing.JSeparator();
        btnDel3DRoi = new javax.swing.JButton();
        chkBxRectrOnAdding = new javax.swing.JCheckBox();
        scrlPane_3D_RoiLst = new javax.swing.JScrollPane();
        gui3DRoiList = new javax.swing.JList<>();
        scrlPane_2D_RoiLst = new javax.swing.JScrollPane();
        gui2DRoiList = new javax.swing.JList<>();
        btnClearAll2D = new javax.swing.JButton();
        showAllRois = new javax.swing.JCheckBox();

        guiSettingsWindow.setTitle("Settings");
        guiSettingsWindow.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        guiSettingsWindow.setLocationByPlatform(true);
        guiSettingsWindow.setMinimumSize(new java.awt.Dimension(500, 500));
        guiSettingsWindow.setName("Settings Tab"); // NOI18N

        javax.swing.GroupLayout guireCtrPropertiesLayout = new javax.swing.GroupLayout(guireCtrProperties);
        guireCtrProperties.setLayout(guireCtrPropertiesLayout);
        guireCtrPropertiesLayout.setHorizontalGroup(
            guireCtrPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );
        guireCtrPropertiesLayout.setVerticalGroup(
            guireCtrPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );

        guiSettingsTab.addTab("Recenter Properties", guireCtrProperties);

        guiautoROIProperties.setPreferredSize(new java.awt.Dimension(461, 354));

        guiroiPrefix.setText("ROI_");

        jLabel3.setText("Roi 2D Prefix");

        gui3DDepth.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        gui3DDepth.setText("10");
        gui3DDepth.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                gui3DDepthActionPerformed(evt);
            }
        });

        jLabel4.setText("3D Roi Depth (nslices)");

        guiroiWidth.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        guiroiWidth.setText("10");

        guiroiHeight.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        guiroiHeight.setText("10");

        jLabel5.setText("Roi Width");

        jLabel6.setText("Roi Height");

        jLabel7.setText("Roi Shape");

        guiShape.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Oval", "Rectangle" }));

        jLabel8.setText("Roi 3D Prefix");

        gui3DroiPrefix.setText("3D");

        gui3DRoiStartNumber.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        gui3DRoiStartNumber.setText("0");

        jLabel9.setText("Start Number for 3D Rois");

        jLabel10.setText("AutoROI Properties:");

        jLabel11.setText("AutoROI Name:");

        jCheckBox1.setText("Resize existing Rois");

        jCheckBox2.setText("Apply to existing");

        javax.swing.GroupLayout guiautoROIPropertiesLayout = new javax.swing.GroupLayout(guiautoROIProperties);
        guiautoROIProperties.setLayout(guiautoROIPropertiesLayout);
        guiautoROIPropertiesLayout.setHorizontalGroup(
            guiautoROIPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, guiautoROIPropertiesLayout.createSequentialGroup()
                .addGroup(guiautoROIPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(guiautoROIPropertiesLayout.createSequentialGroup()
                        .addGap(72, 72, 72)
                        .addGroup(guiautoROIPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel3)
                            .addComponent(jLabel8)
                            .addComponent(jLabel9))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 140, Short.MAX_VALUE)
                        .addGroup(guiautoROIPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(gui3DRoiStartNumber, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(gui3DroiPrefix, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 51, Short.MAX_VALUE)
                            .addComponent(guiroiPrefix, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 51, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addGroup(guiautoROIPropertiesLayout.createSequentialGroup()
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGroup(guiautoROIPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addGroup(guiautoROIPropertiesLayout.createSequentialGroup()
                                .addGap(32, 32, 32)
                                .addComponent(gui3DDepth, javax.swing.GroupLayout.PREFERRED_SIZE, 51, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(guiroiHeight)
                            .addComponent(guiroiWidth)
                            .addComponent(guiShape, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))))
                .addGap(88, 88, 88))
            .addGroup(guiautoROIPropertiesLayout.createSequentialGroup()
                .addGroup(guiautoROIPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(guiautoROIPropertiesLayout.createSequentialGroup()
                        .addGap(74, 74, 74)
                        .addGroup(guiautoROIPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jLabel7, javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel5, javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel6, javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel4, javax.swing.GroupLayout.Alignment.LEADING)))
                    .addGroup(guiautoROIPropertiesLayout.createSequentialGroup()
                        .addGap(15, 15, 15)
                        .addComponent(jLabel10))
                    .addGroup(guiautoROIPropertiesLayout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(jLabel11))
                    .addGroup(guiautoROIPropertiesLayout.createSequentialGroup()
                        .addGap(37, 37, 37)
                        .addComponent(jCheckBox2))
                    .addGroup(guiautoROIPropertiesLayout.createSequentialGroup()
                        .addGap(42, 42, 42)
                        .addComponent(jCheckBox1)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        guiautoROIPropertiesLayout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {gui3DDepth, gui3DRoiStartNumber, gui3DroiPrefix, guiroiHeight, guiroiPrefix, guiroiWidth});

        guiautoROIPropertiesLayout.setVerticalGroup(
            guiautoROIPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(guiautoROIPropertiesLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel11)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(guiautoROIPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(jLabel3)
                    .addComponent(guiroiPrefix, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(guiautoROIPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(jLabel8)
                    .addComponent(gui3DroiPrefix, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(guiautoROIPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(jLabel9)
                    .addComponent(gui3DRoiStartNumber, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jCheckBox2)
                .addGap(20, 20, 20)
                .addComponent(jLabel10)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(guiautoROIPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(gui3DDepth, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel4))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(guiautoROIPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(guiroiHeight, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel6))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(guiautoROIPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(guiroiWidth, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel5))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(guiautoROIPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(guiShape, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel7))
                .addGap(18, 18, 18)
                .addComponent(jCheckBox1)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        guiSettingsTab.addTab("Auto ROI Settings", guiautoROIProperties);

        guiSettingsOkBtn.setText("OK");
        guiSettingsOkBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                guiSettingsOkBtnActionPerformed(evt);
            }
        });

        guiSettingsCancelBtn.setText("Cancel");
        guiSettingsCancelBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                guiSettingsCancelBtnActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout guiSettingsWindowLayout = new javax.swing.GroupLayout(guiSettingsWindow.getContentPane());
        guiSettingsWindow.getContentPane().setLayout(guiSettingsWindowLayout);
        guiSettingsWindowLayout.setHorizontalGroup(
            guiSettingsWindowLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(guiSettingsTab, javax.swing.GroupLayout.DEFAULT_SIZE, 500, Short.MAX_VALUE)
            .addGroup(guiSettingsWindowLayout.createSequentialGroup()
                .addGap(151, 151, 151)
                .addComponent(guiSettingsOkBtn)
                .addGap(30, 30, 30)
                .addComponent(guiSettingsCancelBtn)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        guiSettingsWindowLayout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {guiSettingsCancelBtn, guiSettingsOkBtn});

        guiSettingsWindowLayout.setVerticalGroup(
            guiSettingsWindowLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, guiSettingsWindowLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(guiSettingsTab, javax.swing.GroupLayout.DEFAULT_SIZE, 436, Short.MAX_VALUE)
                .addGap(18, 18, 18)
                .addGroup(guiSettingsWindowLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(guiSettingsCancelBtn)
                    .addComponent(guiSettingsOkBtn))
                .addContainerGap())
        );

        guiSettingsWindowLayout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {guiSettingsCancelBtn, guiSettingsOkBtn});

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Time Series 3D");

        jLabel1.setText("3D Roi List");

        jLabel2.setText("2D Rois");

        mv2Manager.setText("Move to ROI Manager");
        mv2Manager.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                mv2ManagerActionPerformed(evt);
            }
        });

        addto3Dlist.setText("Add 2D Roi to 3D Roi  List");
        addto3Dlist.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addto3DlistActionPerformed(evt);
            }
        });

        remove2Dfrom3D.setText("Remove 2D Roi from 3D ");
        remove2Dfrom3D.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                remove2Dfrom3DActionPerformed(evt);
            }
        });

        transferfromManager.setText("Transfer Rois from Manager ");
        transferfromManager.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                transferfromManagerActionPerformed(evt);
            }
        });

        recenterIn2D.setText("Recenter in 2D");
        recenterIn2D.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                recenterIn2DActionPerformed(evt);
            }
        });

        recenterProperties.setText("Recenter Properties");
        recenterProperties.setEnabled(false);

        buttonExit.setText("Done !");
        buttonExit.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonExitActionPerformed(evt);
            }
        });

        AddOnClick.setText("Add on click");
        AddOnClick.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                AddOnClickActionPerformed(evt);
            }
        });

        btnRecenter3D.setText("Recenter 3D roi in Slices");
        btnRecenter3D.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnRecenter3DActionPerformed(evt);
            }
        });

        btnDefOverlap.setText("Define Overlap");
        btnDefOverlap.setEnabled(false);

        btnMeasure3D.setText("Measure in 3D");
        btnMeasure3D.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnMeasure3DActionPerformed(evt);
            }
        });

        make3Dbutton.setText("Make 3D Roi");
        make3Dbutton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                make3DbuttonActionPerformed(evt);
            }
        });

        buttonAutoRoi.setText("Auto 3D Roi Properties");
        buttonAutoRoi.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonAutoRoiActionPerformed(evt);
            }
        });

        zRecenter.setText("Recenter in all dimensions");
        zRecenter.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                zRecenterActionPerformed(evt);
            }
        });

        btnSetBackGround.setText("Set Background");
        btnSetBackGround.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSetBackGroundActionPerformed(evt);
            }
        });

        btnGenGauInt.setText("Generate Gaussian Objects");
        btnGenGauInt.setEnabled(false);

        btnDetOverlap.setText("Detect Overlap");
        btnDetOverlap.setEnabled(false);

        btnSetMeasurements.setText("Set Measurements");
        btnSetMeasurements.setEnabled(false);

        btnGrp_2D_OR_3D_addOnClk.add(add3D_rad_btn);
        add3D_rad_btn.setText("Add 3D ROi");
        add3D_rad_btn.setEnabled(false);

        btnGrp_2D_OR_3D_addOnClk.add(add2D_rad_btn);
        add2D_rad_btn.setSelected(true);
        add2D_rad_btn.setText("Add 2D ROi");
        add2D_rad_btn.setEnabled(false);

        btnSave3DRois.setText("Save 3D Rois");
        btnSave3DRois.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSave3DRoisActionPerformed(evt);
            }
        });

        btnOpen3DRois.setText("Open 3D Rois");
        btnOpen3DRois.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnOpen3DRoisActionPerformed(evt);
            }
        });

        btnDel3DRoi.setText("Delete 3D Roi(s)");
        btnDel3DRoi.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnDel3DRoiActionPerformed(evt);
            }
        });

        chkBxRectrOnAdding.setText("Recenter while adding");

        javax.swing.GroupLayout panel_3DBtns_ChkBoxLayout = new javax.swing.GroupLayout(panel_3DBtns_ChkBox);
        panel_3DBtns_ChkBox.setLayout(panel_3DBtns_ChkBoxLayout);
        panel_3DBtns_ChkBoxLayout.setHorizontalGroup(
            panel_3DBtns_ChkBoxLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jSep_ChkBox_Btn)
            .addGroup(panel_3DBtns_ChkBoxLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(panel_3DBtns_ChkBoxLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(panel_3DBtns_ChkBoxLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                        .addComponent(btnDel3DRoi, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(buttonAutoRoi, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(btnSetBackGround, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(make3Dbutton, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(zRecenter, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(btnRecenter3D, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(btnMeasure3D, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(btnSetMeasurements, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(btnDetOverlap, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(btnDefOverlap, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(btnGenGauInt, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(btnSave3DRois, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(btnOpen3DRois, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(panel_3DBtns_ChkBoxLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, panel_3DBtns_ChkBoxLayout.createSequentialGroup()
                            .addGap(43, 43, 43)
                            .addGroup(panel_3DBtns_ChkBoxLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                .addComponent(add2D_rad_btn)
                                .addComponent(add3D_rad_btn)))
                        .addComponent(AddOnClick))
                    .addComponent(chkBxRectrOnAdding))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        panel_3DBtns_ChkBoxLayout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {btnDefOverlap, btnDetOverlap, btnGenGauInt, btnMeasure3D, btnRecenter3D, btnSetBackGround, btnSetMeasurements, buttonAutoRoi, make3Dbutton, zRecenter});

        panel_3DBtns_ChkBoxLayout.setVerticalGroup(
            panel_3DBtns_ChkBoxLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_3DBtns_ChkBoxLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(buttonAutoRoi)
                .addGap(1, 1, 1)
                .addComponent(btnSetBackGround)
                .addGap(1, 1, 1)
                .addComponent(make3Dbutton)
                .addGap(1, 1, 1)
                .addComponent(zRecenter)
                .addGap(2, 2, 2)
                .addComponent(btnRecenter3D)
                .addGap(1, 1, 1)
                .addComponent(btnMeasure3D)
                .addGap(1, 1, 1)
                .addComponent(btnSetMeasurements)
                .addGap(1, 1, 1)
                .addComponent(btnDetOverlap)
                .addGap(1, 1, 1)
                .addComponent(btnDefOverlap)
                .addGap(2, 2, 2)
                .addComponent(btnGenGauInt)
                .addGap(1, 1, 1)
                .addComponent(btnSave3DRois)
                .addGap(1, 1, 1)
                .addComponent(btnOpen3DRois)
                .addGap(1, 1, 1)
                .addComponent(btnDel3DRoi)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jSep_ChkBox_Btn, javax.swing.GroupLayout.PREFERRED_SIZE, 10, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(AddOnClick)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(add3D_rad_btn)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(add2D_rad_btn)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(chkBxRectrOnAdding)
                .addContainerGap())
        );

        gui3DRoiList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        gui3DRoiList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                gui3DRoiListValueChanged(evt);
            }
        });
        scrlPane_3D_RoiLst.setViewportView(gui3DRoiList);

        scrlPane_2D_RoiLst.setViewportView(gui2DRoiList);

        btnClearAll2D.setForeground(new java.awt.Color(255, 51, 51));
        btnClearAll2D.setText("Clear List !!");
        btnClearAll2D.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnClearAll2DActionPerformed(evt);
            }
        });

        showAllRois.setText("Show 3D Rois in Slice");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(352, 352, 352)
                        .addComponent(jLabel1)
                        .addGap(194, 194, 194)
                        .addComponent(jLabel2)
                        .addGap(0, 124, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(panel_3DBtns_ChkBox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGap(18, 18, 18)
                        .addComponent(scrlPane_3D_RoiLst, javax.swing.GroupLayout.PREFERRED_SIZE, 247, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(scrlPane_2D_RoiLst, javax.swing.GroupLayout.PREFERRED_SIZE, 228, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)))
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                        .addComponent(mv2Manager, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(addto3Dlist, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(remove2Dfrom3D, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(transferfromManager, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(recenterIn2D, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(recenterProperties, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(btnClearAll2D, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addComponent(showAllRois)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(52, 52, 52)
                        .addComponent(buttonExit, javax.swing.GroupLayout.PREFERRED_SIZE, 102, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );

        layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {addto3Dlist, mv2Manager, recenterIn2D, recenterProperties, remove2Dfrom3D, transferfromManager});

        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(jLabel2))
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(scrlPane_3D_RoiLst)
                    .addComponent(scrlPane_2D_RoiLst)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(panel_3DBtns_ChkBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(mv2Manager)
                        .addGap(2, 2, 2)
                        .addComponent(addto3Dlist)
                        .addGap(7, 7, 7)
                        .addComponent(remove2Dfrom3D)
                        .addGap(4, 4, 4)
                        .addComponent(transferfromManager)
                        .addGap(1, 1, 1)
                        .addComponent(recenterIn2D)
                        .addGap(1, 1, 1)
                        .addComponent(recenterProperties)
                        .addGap(1, 1, 1)
                        .addComponent(btnClearAll2D, javax.swing.GroupLayout.PREFERRED_SIZE, 30, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(showAllRois, javax.swing.GroupLayout.PREFERRED_SIZE, 52, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(buttonExit, javax.swing.GroupLayout.PREFERRED_SIZE, 77, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(52, 52, 52)))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void addto3DlistActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addto3DlistActionPerformed
        
      int[] Idx = this.gui2DRoiList.getSelectedIndices();
      int Index3D = gui3DRoiList.getSelectedIndex();
      if(Index3D > 0 && Idx.length > 0){
        Roi3D tmpRoi = Rois3D.get(Index3D);
        for(int id : Idx){
          Roi roi = Rois2D.get(id);
          tmpRoi.addRoi(roi);
        }
      } 
    }//GEN-LAST:event_addto3DlistActionPerformed

    private void recenterIn2DActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_recenterIn2DActionPerformed
        // TODO add your handling code here:
                currentImp = WindowManager.getCurrentImage();
                int slice = currentImp.getCurrentSlice();
                Roi [] curSliceRois = null;
                
                int count = 0;
                if(Rois3D != null && Rois3D.size() > 0){
                    curSliceRois = new Roi[Rois3D.size()];
                    for(Roi3D tmproi : Rois3D){
                        curSliceRois[count++] = tmproi.get2DRoi(slice);
                    }
                }else{
                    curSliceRois = new Roi[Rois2D.size()];
                    if(!Rois2D.isEmpty()){
                        for(Roi roi : Rois2D){
                        curSliceRois[count++] = roi;
                    }
                    }
                }
               recenter(currentImp,curSliceRois,slice);
         
    }//GEN-LAST:event_recenterIn2DActionPerformed

    private void make3DbuttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_make3DbuttonActionPerformed
        // TODO add your handling code here:
        
       int[] selIdxs = this.gui2DRoiList.getSelectedIndices();
       if(selIdxs.length > 1 ){
           
            this.roi3DCount ++;
            String roiName =  this.roi3DPrefix + "_"+ roi3DCount + "_"+this.roiPrefix ;
            
            Roi [] rois = new Roi[selIdxs.length];
            int count = 0;
            for(Integer Idx : selIdxs){
                rois[count++]  = Rois2D.get(Idx);
            }
           //Roi [] rois = Rois2D.
           Roi3D tmpRoi = new Roi3D(rois);
           tmpRoi.setName(roiName);
           this.Rois3D.add(tmpRoi);
           this.Roi3DListModel.addElement(tmpRoi.getName());
       }
       
       
        
    }//GEN-LAST:event_make3DbuttonActionPerformed

    private void mv2ManagerActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mv2ManagerActionPerformed
        
        //int count  = Rois2D.size();
        int [] selectedIndx = gui2DRoiList.getSelectedIndices();
        if(selectedIndx.length == 0)
           for(Roi roi : Rois2D)
               Manager.addRoi(roi);
        else
            for( int roiIdx : selectedIndx){
                Manager.addRoi(this.Rois2D.get(roiIdx));
        }
       // this.Manager.addRoi();
            
            
    }//GEN-LAST:event_mv2ManagerActionPerformed

    private void remove2Dfrom3DActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_remove2Dfrom3DActionPerformed
        // TODO add your handling code here:
        int [] selectedIndx = gui2DRoiList.getSelectedIndices();
        int Idx3D = gui3DRoiList.getSelectedIndex();
        if(Idx3D != -1){
            for( int roiIdx : selectedIndx){
                if(!this.Rois3D.get(Idx3D).remove(Rois2D.get(roiIdx)))
                    //display a error message;
                this.removeRoi(Rois2D.get(roiIdx));
            }
        }
    }//GEN-LAST:event_remove2Dfrom3DActionPerformed

    private void transferfromManagerActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_transferfromManagerActionPerformed
        
        Roi [] selRois = Manager.getSelectedRoisAsArray();
        
        for(Roi roi : selRois){
            this.addNewRoi(roi);
        }
    }//GEN-LAST:event_transferfromManagerActionPerformed

    private void btnClearAll2DActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnClearAll2DActionPerformed
        Roi2DListModel.clear();
        Rois2D.clear();
    }//GEN-LAST:event_btnClearAll2DActionPerformed

    private void AddOnClickActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_AddOnClickActionPerformed
        // TODO add your handling code here:
        if(AddOnClick.isSelected()){
            add3D_rad_btn.setEnabled(true);
            add2D_rad_btn.setEnabled(true);
        }
        else{
            add3D_rad_btn.setEnabled(false);
            add2D_rad_btn.setEnabled(false);
        }
            
    }//GEN-LAST:event_AddOnClickActionPerformed

    private void btnSave3DRoisActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSave3DRoisActionPerformed
        // TODO add your handling code here:
        int Idx = this.gui3DRoiList.getSelectedIndex();
        
        Roi3D tmpRoi = new Roi3D(); 
        
        JFileChooser FC = new JFileChooser(this.defaultPath);
        FC.setDialogType(JFileChooser.SAVE_DIALOG);
        FC.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int Status = FC.showSaveDialog(null);
       
        if(Status == JFileChooser.APPROVE_OPTION){
            File fileOut = FC.getSelectedFile();
            if(Idx == -1){
                for(Roi3D roi : Rois3D){
                    File fOut = new File(fileOut.getName()+ File.separator +roi.getName()+".3Dr");
                    if(fOut!=null)
                        try {
                            roi3DFileSaver(fileOut,roi);
                    } catch (IOException ex) {
                        Logger.getLogger(TimeSeries_3D_Analyser_Beta_2dot1.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }else{
                
                try {
                    roi3DFileSaver(fileOut,Rois3D.get(Idx));
                } catch (IOException ex) {
                    Logger.getLogger(TimeSeries_3D_Analyser_Beta_2dot1.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
           // roi3DFileSaver(fileOut, tmpRoi);
        }
        
        
        
        
        
    }//GEN-LAST:event_btnSave3DRoisActionPerformed

    private void roi3DFileSaver(File fileOut, Roi3D tmpRoi) throws IOException {
        //File fileOut = new File();
        if(fileOut != null){
           
            ZipOutputStream fOut = null;
            ZipEntry ze = new ZipEntry("");
            try {
                String fName  = fileOut.getPath() + File.separator+tmpRoi.getName()+".zip";
                System.out.print(fName);
                fOut = new ZipOutputStream(new FileOutputStream(fName));
            } catch (FileNotFoundException ex) {
                Logger.getLogger(TimeSeries_3D_Analyser_Beta_2dot1.class.getName()).log(Level.SEVERE, null, ex);
            }
            Manager.reset();
            Roi [] rois = tmpRoi.getRoiSet();
            RoiEncoder encoder = new RoiEncoder(fOut);
            for (Roi roi : rois){
                ze = new ZipEntry(roi.getName()+".roi");
                fOut.putNextEntry(ze);
                encoder.write(roi);
                fOut.closeEntry();
            }
            fOut.close();
            
        }
    }
    private Roi3D roi3DFileReader(File roiFile){
        Roi3D tmpRoi = new Roi3D();
        ZipInputStream zin;
        byte [] buffer = new byte[2048];
        ByteArrayOutputStream  dataBuff;
        ZipEntry ze;
        String RoiName;
        int len = 0;
        
        if(roiFile != null){
            
            try{
                zin = new ZipInputStream(new FileInputStream (roiFile));
                
                while((ze = zin.getNextEntry()) != null){
                    dataBuff = new ByteArrayOutputStream();
                    RoiName = ze.getName();
                    
                    while((len = zin.read(buffer))> 0)
                            dataBuff.write(buffer,0, len);
                    
                    dataBuff.close();
                    RoiDecoder decoder = new RoiDecoder(dataBuff.toByteArray(),RoiName);
                    Roi roi = decoder.getRoi();
                    tmpRoi.addRoi(roi);
                    
                }
                /*ObjectInputStream Oin = new ObjectInputStream(fIn);
                tmpRoi = (Roi3D)Oin.readObject();*/
                
                
            }catch(IOException IE){
            
            }  
            String nameStr = roiFile.getName();
            int endIdx = nameStr.lastIndexOf(".");
            tmpRoi.setName(nameStr.substring(0,endIdx));
        }
        
        return tmpRoi;
    }

    private void btnOpen3DRoisActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnOpen3DRoisActionPerformed
        // TODO add your handling code here:
        JFileChooser fileOpener = new JFileChooser(this.defaultPath);
        fileOpener.setDialogType(JFileChooser.FILES_ONLY);
        fileOpener.setMultiSelectionEnabled(true);
        int status = fileOpener.showOpenDialog(this);
        File [] selFiles;
        Roi3D tmpRoi;
        
        if(status == JFileChooser.APPROVE_OPTION){
            selFiles = fileOpener.getSelectedFiles();
            for(File f :selFiles){
               tmpRoi = roi3DFileReader(f);
               this.Rois3D.add(tmpRoi);
               this.Roi3DListModel.addElement(tmpRoi.getName());
               this.roi3DCount++;
            }
        }
    }//GEN-LAST:event_btnOpen3DRoisActionPerformed

    private void buttonAutoRoiActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonAutoRoiActionPerformed
        // TODO add your handling code here:
        
        start3DRoiNumber = cur3DRoiNumber;
        gui3DRoiStartNumber.setText(""+roi3DCount);
        this.gui3DroiPrefix.setText(roi3DPrefix);
        this.guiroiPrefix.setText(roiPrefix);
        this.guiroiHeight.setText(""+this.roiHeight);
        this.guiroiWidth.setText(""+this.roiWidth);
        this.gui3DDepth.setText(""+this.roiDepth);
        this.guiShape.setSelectedIndex(this.roiShape.ordinal());
        
        this.guiSettingsWindow.setVisible(true);
        
        
    }//GEN-LAST:event_buttonAutoRoiActionPerformed

    private void guiSettingsOkBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_guiSettingsOkBtnActionPerformed
        // TODO add your handling code here:
        this.roiPrefix = this.guiroiPrefix.getText();
        this.roi3DPrefix = this.gui3DroiPrefix.getText();
        
        this.start3DRoiNumber = Integer.parseInt(this.gui3DRoiStartNumber.getText());
        
        this.roiHeight = Integer.parseInt(guiroiHeight.getText());
        this.roiWidth = Integer.parseInt(guiroiWidth.getText());
        this.roiDepth = Integer.parseInt(gui3DDepth.getText());
        
        this.guiSettingsWindow.setVisible(false);
        
    }//GEN-LAST:event_guiSettingsOkBtnActionPerformed

    private void guiSettingsCancelBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_guiSettingsCancelBtnActionPerformed
        // TODO add your handling code here:
        this.guiSettingsWindow.setVisible(false);
    }//GEN-LAST:event_guiSettingsCancelBtnActionPerformed
    private void recenterInZ(){
        
        //ArrayList selList;
        int [] selListIdx;
        selListIdx = gui3DRoiList.getSelectedIndices();
        
        if(selListIdx.length == 0){                             //if no 3D roi is selected then select all
            int listSz = gui3DRoiList.getModel().getSize();
            selListIdx = new int[listSz];
            for(int Count = 0 ; Count < listSz ; Count++){
                selListIdx[Count] = Count;
            }
        }
        int zConvergeLimit = 1;
        int maxIterations = 20;
        currentImp = WindowManager.getCurrentImage();
        for(int Idx : selListIdx){
            Roi3D roi3D = Rois3D.get(Idx);
            boolean converged = false;
            int iterations = 0;
            
            while(iterations++ < maxIterations && !converged){
                //first find the maximum (intensity) ideal to allow the user to set anyone of the many parameters 
                //that imagestatistics can measure to truely havea control over how to center it. For eg. one could 
                //use integrated density instead of mean intensity.
                
                double maxIntensity = 0;
                double curIntensity ;
                int zatmaxIntensity = 0;
                
                for(int sliceCount = roi3D.getStartSlice() ; sliceCount < roi3D.getEndSlice() ; sliceCount++){

                    Roi sliceRoi = roi3D.get2DRoi(sliceCount);
                    //if(currentImp == null)
                      //  currentImp = WindowManager.getCurrentImage();
                      if(sliceRoi != null){
                        currentImp.setSlice(sliceCount);
                        currentImp.setRoi(sliceRoi);
                        ImageStatistics stat = currentImp.getStatistics(ImageStatistics.MEAN);
                        curIntensity = stat.mean;
                        if(maxIntensity < curIntensity){
                            maxIntensity = curIntensity;
                            zatmaxIntensity = sliceCount;
                        }
                      }
                }
                int zDiff =  zatmaxIntensity - roi3D.getCenterZ();
                System.out.print("\n Iteration #: " + iterations + " maxIntensity " +maxIntensity + "zof Max : "+ zatmaxIntensity +"present diff: " +zDiff );
                if(Math.abs(zDiff) > zConvergeLimit){
                    roi3D.repositionZ(zDiff);
                    recenter(roi3D);
                }else{
                    converged = true;
                }
            }
        }
    }
    private void btnRecenter3DActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRecenter3DActionPerformed
        // TODO add your handling code here:
        
        int roiCount;
        currentImp = WindowManager.getCurrentImage();
        int maxSlice = this.currentImp.getStackSize();
        int presentSlice = currentImp.getSlice();
        
            for( int curSlice  = 1 ; curSlice < maxSlice ; curSlice++){
                //currentImp.setSlice(curSlice);
                Roi [] curSliceRois = new Roi[Rois3D.size()];
                roiCount = 0;
                Roi roi2D = null;
                if(Rois3D != null && Rois3D.size() > 0){
                    for(Roi3D tmproi : Rois3D){
                        if((roi2D = tmproi.get2DRoi(curSlice)) != null)
                            curSliceRois[roiCount++] = roi2D;
                            //recenter(currentImp,roi2D,curSlice);
                        else
                            ;//this 3Droi does not have its 2D roi in this slice
                    }
                  if(roiCount>0)
                      recenter(currentImp,curSliceRois,curSlice);  
                }
                
            }
           
        currentImp.setSlice(presentSlice);
    }//GEN-LAST:event_btnRecenter3DActionPerformed

    private void btnDel3DRoiActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnDel3DRoiActionPerformed
        // TODO add your handling code here:
        int idx = this.gui3DRoiList.getSelectedIndex();
        if(idx == -1){
            int confirm = JOptionPane.showConfirmDialog(this, "No 3D Rois is selected. Delete All ?");
            if (confirm == JOptionPane.OK_OPTION || confirm == JOptionPane.YES_OPTION){
                Roi3DListModel.removeAllElements();
                Rois3D.removeAll(Rois3D);
                roi3DCount = 0;
            }
        }
        
        this.Roi3DListModel.remove(idx);
        this.Rois3D.remove(idx);
        this.roi3DCount--;
    }//GEN-LAST:event_btnDel3DRoiActionPerformed

    private void buttonExitActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonExitActionPerformed
        // TODO add your handling code here:
        this.setVisible(false);
        this.dispose();
    }//GEN-LAST:event_buttonExitActionPerformed

    private void gui3DRoiListValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_gui3DRoiListValueChanged
        // TODO add your handling code here:        
        this.deconstruct3Dto2D(gui3DRoiList.getSelectedIndex());
    }//GEN-LAST:event_gui3DRoiListValueChanged

    private void btnMeasure3DActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnMeasure3DActionPerformed
        // TODO add your handling code here:
        ResultsTable rt = new ResultsTable();
        currentImp = WindowManager.getCurrentImage();
        int stkSize = currentImp.getStackSize();
        ImageStatistics stat;
        //Roi[] rois;
        
        for(int curSlice = 1 ; curSlice < stkSize ; curSlice++){
            rt.incrementCounter();
            currentImp.setSlice(curSlice);
                       
            for(Roi3D tmpRoi : Rois3D){
                Roi roi = tmpRoi.get2DRoi(curSlice);
                if(roi != null){
                    currentImp.setRoi(roi, true);
                    stat = currentImp.getStatistics(Measurements.MEAN);
                    rt.addValue(tmpRoi.getName(), stat.mean);
                }  
            }           
        }
        
        rt.show("3D Roi Mean Measurements");
        rt.showRowNumbers(true);
        
        if(true /*Gaussian Fits*/){
            int nCols = rt.getHeadings().length;
            ResultsTable fitRes = new ResultsTable();
            ResultsTable GaussOffsetFits = new ResultsTable();
            
            
            //ResultsTable trimmedData = new ResultsTable();
            
            for(int count = 0 ; count < nCols ; count++ ){
                String Label = rt.getColumnHeading(count);
                double[] data = rt.getColumnAsDoubles(count);
                double[] xData = new double[data.length];
                double[] yData = new double[data.length];
                int x = 1;
                int dataCount = 0;
                for(double y : data){
                    x++;
                    if(y != 0){
                        xData[dataCount] = x;
                        yData[dataCount] = y;
                        dataCount++;
                    }
                }
                CurveFitter fitter = new CurveFitter(xData,yData);
                fitter.doFit(CurveFitter.GAUSSIAN_NOOFFSET);
                
                double [] params = fitter.getParams();
                double [] params2 = new double[params.length];
                
                IJ.log(fitter.getResultString() +"\n"+ " /***/ "+"\n" + fitter.getStatusString());
                
                fitRes.incrementCounter();
                fitRes.addLabel(Label);
                int pCount = 0;
                for(double value : params){
                    fitRes.addValue("P"+ pCount++, value);
                }
                
                fitRes.addValue("Goodness of Fit", fitter.getFitGoodness());
                fitRes.addValue("RSquared", fitter.getRSquared());
                //fitRes.show("Gaussian Fits");
                 // System.arraycopy(params, 0, params2, 2, params.length-1);
                
                /**Calculation of Initial Parameters 
                 **/
                params2[0] = this.bgd != 0 ? bgd : 0;
                params2[1] = params[0] /*+ this.bgd*/;
                params2[2] = params[1];
                params2[3]  = params[2];
              
                double [] parVar = new double[params2.length];
                pCount = 0;
                for(double para : params2)
                    parVar[pCount++] = 0.1*para;
                
                CurveFitter fitterOffset = new CurveFitter(xData,yData);
                fitterOffset.setInitialParameters(params2);
                fitterOffset.getMinimizer();
                fitterOffset.doFit(CurveFitter.GAUSSIAN);
                //fitterOffset.doCustomFit("y = a + b * exp(-(x-c)*(x-c)/(2*d*d)", params2, false);
                
                double [] params3 = fitterOffset.getParams();
                IJ.log(fitterOffset.getResultString() +"\n"+ "/***/"+"\n" + fitterOffset.getStatusString());
                
                GaussOffsetFits.incrementCounter();
                GaussOffsetFits.addLabel(Label);
                int p2Count = 0;
                for(double value : params3){
                    GaussOffsetFits.addValue("P"+ p2Count++, value);
                    
                }
                p2Count = 0;
                for(double value : params2){
                    GaussOffsetFits.addValue("Initial P"+p2Count++,value);
                }
               GaussOffsetFits.addValue("Goodness of Fit",  fitterOffset.getFitGoodness());
               GaussOffsetFits.addValue("RSquared",  fitterOffset.getRSquared());
            
            }
            fitRes.show("Gaussian Fits");
            GaussOffsetFits.show("Gaussian Fits with Offsets");
            
           
            
        }
        
    }//GEN-LAST:event_btnMeasure3DActionPerformed

    private void gui3DDepthActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_gui3DDepthActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_gui3DDepthActionPerformed

    private void btnSetBackGroundActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSetBackGroundActionPerformed
        this.btnMeasure3D.setEnabled(true);
        if(Rois2D.isEmpty()){
            
        }else{
            ShapeRoi combination = new ShapeRoi(Rois2D.get(1));
            ShapeRoi sr;
            for(Roi roi : Rois2D){
                sr = new ShapeRoi(roi);
                combination.or(sr);
            }
            if(combination != null ){
                currentImp = WindowManager.getCurrentImage();
                currentImp.setRoi(combination);
                ImageStatistics stat = currentImp.getStatistics(Measurements.MEAN);

                this.bgd = stat.mean;
            }
        }   
    }//GEN-LAST:event_btnSetBackGroundActionPerformed

    private void zRecenterActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zRecenterActionPerformed
        // TODO add your handling code here:
        this.recenterInZ();
    }//GEN-LAST:event_zRecenterActionPerformed

    public void recenter(ImagePlus imp, Roi[] rois, int sliceNo){
        recentering = true; //started
        if(imp != null && rois != null && sliceNo > 0){
            
            //imp.lock();
            imp.setSlice(sliceNo);
            imp.updateAndDraw();
            System.out.println("The slice number is:"+ sliceNo);
                        
            /* variables that need user inputs            */
            
            double calReq = 0.5;           
            int MaxIteration = 20;
            double CLimit = 0.1;
            
            ImageStatistics stat;//new ImageStatistics();
            Calibration calib = imp.getCalibration();
            double xScale = calib.pixelWidth;
            double yScale = calib.pixelHeight;
            boolean Converge = false;
            
            int New_x = 0;
            int New_y = 0;
            
            double xMovement = 0, yMovement = 0;
            java.awt.Rectangle Boundary;
                    
            for(Roi orgRoi : rois){
                
                if(orgRoi == null)
                    break;
                else{
                    Boundary = orgRoi.getBounds();
                    double scaledWidth = orgRoi.getFloatWidth()*calReq;
                    double scaledHeight = orgRoi.getFloatHeight()*calReq;
                    Roi CurRoi = new Roi((Boundary.getCenterX()-(scaledWidth/2)),(Boundary.getCenterY()-(scaledHeight/2)),roiWidth*calReq,roiHeight*calReq);
                                    //Using a rectangle Roi for estimating the center
                    Converge = false;               
                    imp.setRoi(CurRoi);
                    imp.updateAndDraw();
                    stat = imp.getStatistics(Measurements.CENTER_OF_MASS + Measurements.CENTROID);

                    for(int Iteration = 1 ; Iteration <= MaxIteration  && !Converge; Iteration++){

                        stat = imp.getStatistics(Measurements.CENTER_OF_MASS + Measurements.CENTROID); //Calculate center of Mass and Centroid; 
                        New_x = (int) Math.round(((stat.xCenterOfMass/xScale) - (scaledWidth/2.0)));
                        New_y = (int) Math.round(((stat.yCenterOfMass/yScale) - (scaledHeight/2.0)));
      /*for debugging purposes*/ System.out.println("Recentering Started: Iteration " +Iteration+" Center of Mass (x,y): "+ stat.xCenterOfMass+", "+stat.yCenterOfMass+ "Centroid: "+ stat.xCentroid +"," +stat.yCentroid);
                        // Calculate movements
                        xMovement =(stat.xCentroid - stat.xCenterOfMass)/xScale;
                        yMovement = (stat.yCentroid - stat.yCenterOfMass)/yScale;
                        if( Math.abs(xMovement) < 1 && xMovement != 0 && yMovement != 0 && Math.abs(yMovement) < 1){ //Now search nearby;
                            if(Math.abs(xMovement) > Math.abs(yMovement)){
                                New_x = (xMovement > 0) ? (int)Math.round(stat.xCentroid/xScale - (scaledWidth/2.0) - 1) : (int)Math.round(stat.xCentroid/xScale - (scaledWidth/2.0) + 1);
                                New_y = (int) Math.round(stat.yCentroid/yScale - (scaledHeight/2.0));
                            }
                            else{
                                New_y = (yMovement > 0) ? (int)Math.round(stat.yCentroid/yScale -(scaledHeight/2.0)- 1) : (int)Math.round(stat.yCentroid/yScale - (scaledHeight/2.0)+ 1);
                                New_x = (int) Math.round(stat.xCentroid/xScale -(scaledWidth/2.0));
                            }
                        }
                        else{
                            New_x = (int)Math.round (((stat.xCenterOfMass/xScale) - (scaledWidth/2.0)));
                            New_y = (int)Math.round (((stat.yCenterOfMass/yScale) - (scaledHeight/2.0)));

                        }
                        Converge = ( Math.abs(xMovement) < CLimit && Math.abs(yMovement) < CLimit) ;
                        CurRoi.setLocation(New_x ,New_y);
                        imp.setRoi(CurRoi);
                        imp.updateAndDraw();
                    }
                    orgRoi.setLocation((stat.xCentroid - (roiWidth/2.0)), (stat.yCentroid - (roiHeight/2.0)));
                    //imp.setRoi(orgRoi);
                    
                }
            }
        }
        
      recentering = false;  //done
    }
    @Override
    public void mouseClicked(MouseEvent me) {
       // throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        
       if (AddOnClick.isSelected()){
                int x = me.getX();
                int y = me.getY();
                
                //int Width = 10;             //Need to be set in AutoRoi Properties
                //int Height = 10;            //Need to be set in AutoRoi Properties
                
                ImagePlus imp = WindowManager.getCurrentImage();
                if (imp != null){
                    ImageWindow Win = imp.getWindow();
                    ImageCanvas canvas = Win.getCanvas();

                    int offscreenX = canvas.offScreenX(x);
                    int offscreenY = canvas.offScreenY(y);
                    int Start_x = offscreenX - (int)(roiWidth/2);
                    int Start_y = offscreenY - (int)(roiHeight/2);
                    
                    int z = imp.getSlice();
                    
                   
                    if(add2D_rad_btn.isSelected()){
                        Roi tmpRoi = new OvalRoi(Start_x,Start_y,roiWidth,roiHeight);
                        tmpRoi.setName(this.roiPrefix+"_"+Start_x + "_"+Start_y+"_"+z);
                        tmpRoi.setLocation(Start_x,Start_y);
                        tmpRoi.setPosition(z);
                        imp.setRoi(tmpRoi, true);
                        Manager.addRoi(tmpRoi);
                        //if(this.chkBxRectrOnAdding.isSelected())
                          //                   recenter(imp, tmpRoi,z);
                        //this.addNewRoi(tmpRoi);
                    }
                    if(add3D_rad_btn.isSelected()){
                        
                        int endPosition = z +roiDepth/2;
                        int startPosition = z > roiDepth/2 ? z -roiDepth/2 : 0 ;
                        Roi3D tmp3DRoi = new Roi3D();
                       
                        tmp3DRoi.setName(roi3DPrefix+"_"+ roi3DCount++ + roiPrefix);
                        tmp3DRoi.setCenterZ(z);
                        tmp3DRoi.setnSlices(endPosition - startPosition);
                        
                        Roi [] rois = new Roi[roiDepth];     
                        for(int curPos = startPosition,count = 0 ; curPos < endPosition ; curPos++,count++){
                             Roi tmpRoi = new OvalRoi(Start_x,Start_y,roiWidth,roiHeight);
                             tmpRoi.setName(roiPrefix+"_"+Start_x + "_"+Start_y+"_"+ curPos);
                             tmpRoi.setLocation(Start_x,Start_y);
                             tmpRoi.setPosition(curPos);
                             imp.setRoi(tmpRoi, true);
                             if(this.chkBxRectrOnAdding.isSelected())
                                             recenter(imp, tmpRoi,curPos);
                             //Manager.addRoi(tmpRoi);
                             this.addNewRoi(tmpRoi);
                             rois[count] = tmpRoi;    
                        }
                        tmp3DRoi.addRoiSet(rois);
                        Rois3D.add(tmp3DRoi);
                        this.Roi3DListModel.addElement(tmp3DRoi.getName());
                        imp.setSlice(z);
                    //this.addNewRoi(tmpRoi);
                    }
                }
        }
    
    
    }

    @Override
    public void mousePressed(MouseEvent me) {
       // throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void mouseReleased(MouseEvent me) {
        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void mouseEntered(MouseEvent me) {
       // throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void mouseExited(MouseEvent me) {
        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    @Override
    public void imageOpened(ImagePlus imp) {
        currentImp = WindowManager.getCurrentImage()!= null ?  WindowManager.getCurrentImage():null;      
        this.currCanvas = (activeImage = (currentImp != null)) ? currentImp.getCanvas(): null;
        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void imageClosed(ImagePlus imp) {
        currentImp = WindowManager.getCurrentImage()!= null ?  WindowManager.getCurrentImage():null;      
        this.currCanvas = (activeImage = (currentImp != null)) ? currentImp.getCanvas(): null;
        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void imageUpdated(ImagePlus imp) {
        //boolean showROis = true;
        //throw new UnsupportedOperati onException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        if(this.showAllRois.isSelected() && ! recentering){                     //recentering will be turned on and off depending on if the recentering is in progress
            Roi roi;
            ShapeRoi combinedRoi = new ShapeRoi(new Roi(0,0,0,0));
            int curSlice = currentImp.getSlice();
            int selIdx = this.gui3DRoiList.getSelectedIndex();
            if(selIdx == -1){
                for(Roi3D roi3D : Rois3D){
                    //ShapeRoi tmpSR = ((roi = roi3D.get2DRoi(curSlice)) != null) ? new ShapeRoi(roi):null;
                    roi = roi3D.get2DRoi(curSlice);
                    if(roi != null){
                        ShapeRoi tmpSR = new ShapeRoi(roi);
                        combinedRoi.or(tmpSR);
                    }

                }
                currentImp.setRoi(combinedRoi);
            }else{
                roi = Rois3D.get(selIdx).get2DRoi(selIdx);
                currentImp.setRoi(roi);
            }
            
        }
        
    }

   
    

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox AddOnClick;
    private javax.swing.JRadioButton add2D_rad_btn;
    private javax.swing.JRadioButton add3D_rad_btn;
    private javax.swing.JButton addto3Dlist;
    private javax.swing.JButton btnClearAll2D;
    private javax.swing.JButton btnDefOverlap;
    private javax.swing.JButton btnDel3DRoi;
    private javax.swing.JButton btnDetOverlap;
    private javax.swing.JButton btnGenGauInt;
    private javax.swing.ButtonGroup btnGrp_2D_OR_3D_addOnClk;
    private javax.swing.JButton btnMeasure3D;
    private javax.swing.JButton btnOpen3DRois;
    private javax.swing.JButton btnRecenter3D;
    private javax.swing.JButton btnSave3DRois;
    private javax.swing.JButton btnSetBackGround;
    private javax.swing.JButton btnSetMeasurements;
    private javax.swing.JButton buttonAutoRoi;
    private javax.swing.JButton buttonExit;
    private javax.swing.JCheckBox chkBxRectrOnAdding;
    private javax.swing.JList<String> gui2DRoiList;
    private javax.swing.JTextField gui3DDepth;
    private javax.swing.JList<String> gui3DRoiList;
    private javax.swing.JTextField gui3DRoiStartNumber;
    private javax.swing.JTextField gui3DroiPrefix;
    private javax.swing.JButton guiSettingsCancelBtn;
    private javax.swing.JButton guiSettingsOkBtn;
    private javax.swing.JTabbedPane guiSettingsTab;
    private javax.swing.JFrame guiSettingsWindow;
    private javax.swing.JComboBox<String> guiShape;
    private javax.swing.JPanel guiautoROIProperties;
    private javax.swing.JPanel guireCtrProperties;
    private javax.swing.JTextField guiroiHeight;
    private javax.swing.JTextField guiroiPrefix;
    private javax.swing.JTextField guiroiWidth;
    private javax.swing.JCheckBox jCheckBox1;
    private javax.swing.JCheckBox jCheckBox2;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JSeparator jSep_ChkBox_Btn;
    private javax.swing.JButton make3Dbutton;
    private javax.swing.JButton mv2Manager;
    private javax.swing.JPanel panel_3DBtns_ChkBox;
    private javax.swing.JButton recenterIn2D;
    private javax.swing.JButton recenterProperties;
    private javax.swing.JButton remove2Dfrom3D;
    private javax.swing.JScrollPane scrlPane_2D_RoiLst;
    private javax.swing.JScrollPane scrlPane_3D_RoiLst;
    private javax.swing.JCheckBox showAllRois;
    private javax.swing.JButton transferfromManager;
    private javax.swing.JButton zRecenter;
    // End of variables declaration//GEN-END:variables

    private void addNewRoi(Roi roi) {
        //throw new UnsupportedOperationException("Not supported yet.");// To change body of generated methods, choose Tools | Templates.
        if(roi != null){
            this.Roi2DListModel.addElement(roi.getName());                  // Add the new roi to the list model which contains the data that is being displyed 
                                                                            // in the guiRoi2D List. This only adds the name of the roi
            this.Rois2D.add(roi);                                           // Ensure that the roi is added to the arraylist of the 2DRois
        //this.gui2DRoiList.setModel(Roi2DListModel);                       // Not sure if we need this but just in case update the model after addition
        }else{
            
        }  
    }
    private void removeRoi(Roi roi){
        
        boolean status = this.Rois2D.remove(roi);
        if(status == false)
            ;   //Throw error message 
        else
            this.Roi2DListModel.removeElement(roi.getName());
        
        this.gui2DRoiList.setModel(Roi2DListModel);
    }

    @Override
    public void run() {
        
		while (!done) {
			try {Thread.sleep(500);}
			catch(InterruptedException e) {}
			ImagePlus imp = WindowManager.getCurrentImage();
			if (imp != null){
				ImageCanvas canvas = imp.getCanvas();
                          	if (canvas != previousCanvas){
					if(previousCanvas != null)
                                               previousCanvas.removeMouseListener(this);
					canvas.addMouseListener(this);
					previousCanvas = canvas;
				}
			}
                        else{
                            if(previousCanvas != null)
                                previousCanvas.removeMouseListener(this);
                            previousCanvas = null;
                        }
                           
		}
	}
        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    
}
