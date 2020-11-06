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
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import java.awt.HeadlessException;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import static java.lang.Math.sqrt;
import static java.lang.Thread.sleep;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/**
 * There was a serious bug in the fitting routine: The array need to trimmed to
 * eliminate zeros. In the previous versions this was not taken care of leading
 * to not a good fit. The default path is now set using the imageplus. Have
 * introduced movement restriction during recentering. Measures the total
 * movement in x and y components and then compares the total radial distance to
 * ferret Diameter of the ROI (Ferest * resFac). The measurement results are
 * named using the imageplus and then saved in a data folder that gets set
 * during the first setup.
 *
 *
 *
 * @author Balaji
 */
public class TimeSeries_3D_Analyser extends javax.swing.JFrame implements Runnable, MouseListener/*required for add on click*/, ImageListener /*required for knowing if the active image is been closed*/ {

    RoiManager Manager;               //Handle to store and access the native ROIManager Instance. 
    ImagePlus currentImp, currSlice;   //Place holders to store and refer the currently active imageplus and the current displayed slice
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

    //guiTranslateRoi roiTranslator = new guiTranslateRoi();
    /**
     * Settings options for autoROI properties are managed in the section bellow
     */
    String autoROIPrefix;
    private String roiPrefix = "ROI";
    private String roi3DPrefix = "3D";
    private int start3DRoiNumber = 0;
    private int cur3DRoiNumber = 0;
    // private int cur2DRoiNumber = 0;
    private int roiHeight = 17;
    private int roiWidth = 17;
    private int roiDepth = 23;
    private int roi3DCount = start3DRoiNumber;
    //private int roi2DCount = 0;
    private File defaultPath;
    private double bgd;
    private boolean recentering;
    private ShapeRoi combinedRoi;
    private int xShiftTotal;
    private int yShiftTotal;
    private int zShiftTotal;
    private String resultsDirectory = null;
    private ResultsTable Peaks;
    private String prevFilename = "";
    private File[] selFiles;

    private void deconstruct3Dto2D(int selIdx) {

        if (selIdx != -1) {
            Roi[] sel2DRois = Rois3D.get(selIdx).getRoiSet();
            this.btnClearAll2DActionPerformed(null);

            for (Roi roi : sel2DRois) {
                addNewRoi(roi);
            }
        } else {
            btnClearAll2DActionPerformed(null);
            for (Roi3D roi3D : Rois3D) {
                Roi[] roi2DArray = roi3D.getRoiSet();
                for (Roi roi2D : roi2DArray) {
                    addNewRoi(roi2D);
                }
            }

        }
    }

    public void recenter(ImagePlus imp, Roi tmpRoi, int z) {
        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        Roi[] rois = new Roi[1];
        rois[0] = tmpRoi;
        recenter(imp, rois, z);
    }

    private void recenter(Roi3D roi3D) {
        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.

        for (Roi roi : roi3D.getRoiSet()) {
            recenter(this.currentImp, roi, roi.getPosition());
        }
    }

    enum roiType {
        OVAL, RECTANGLE
    };
    private roiType roiShape = roiType.OVAL;

    /**
     * Creates new form TimeSeries_3D_Analyser
     */
    public TimeSeries_3D_Analyser() {

        if (RoiManager.getInstance() == null) {       //No previous instance of Roi Manager; User has not invoked the ROIManager tool yet. 
            Manager = new RoiManager();            //Create a new instance of the RoiManager and obtain a handle to it.
        } else {
            Manager = RoiManager.getInstance();     //Roi MAnager is in use. Get the instance handle and store it for us to use.
        }

        currentImp = ij.WindowManager.getCurrentImage(); //Obtain the  currently displayed image in ImageJ If multiple images are open we get the imageplus of the active window.
        //If none of the images are open, the windowmanager returns null and it is stored in currentImp. 
        if (currentImp != null) {
            activeImage = true;   // The windowmanger of ImageJ returned a non-null value. There is an active image.
            defaultPath = new File(currentImp.getFileInfo().directory);
        } else {
            currCanvas = null;                          // The windowmanager returned null => no images open. So no canvas and active image is set to false
            activeImage = false;
        }
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(TimeSeries_3D_Analyser.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            Logger.getLogger(TimeSeries_3D_Analyser.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            Logger.getLogger(TimeSeries_3D_Analyser.class.getName()).log(Level.SEVERE, null, ex);
        } catch (UnsupportedLookAndFeelException ex) {
            Logger.getLogger(TimeSeries_3D_Analyser.class.getName()).log(Level.SEVERE, null, ex);
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
        thread = new Thread(this, "Time Series ");
        thread.setPriority(Math.max(thread.getPriority() - 2, Thread.MIN_PRIORITY));
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

        TranslateRoi = new javax.swing.JFrame();
        closeButton = new javax.swing.JButton();
        labelTitle = new javax.swing.JLabel();
        panelMvbyPix = new javax.swing.JPanel();
        txt_yDist = new javax.swing.JTextField();
        txt_xDist = new javax.swing.JTextField();
        txt_zDist = new javax.swing.JTextField();
        xPosLabel = new javax.swing.JLabel();
        zPosLabel = new javax.swing.JLabel();
        yPosLabel = new javax.swing.JLabel();
        btnMove = new javax.swing.JButton();
        radBtn_RelativeMove = new javax.swing.JRadioButton();
        radBtn_AbsMove = new javax.swing.JRadioButton();
        panelClick2Move = new javax.swing.JPanel();
        btnNorth = new javax.swing.plaf.basic.BasicArrowButton(SwingConstants.NORTH);
        btnEast = new javax.swing.plaf.basic.BasicArrowButton(SwingConstants.EAST);
        btnWest = new javax.swing.plaf.basic.BasicArrowButton(SwingConstants.WEST);
        btnSouth = new javax.swing.plaf.basic.BasicArrowButton(SwingConstants.SOUTH);
        btnResetinMove = new javax.swing.JButton();
        btnZdn = new javax.swing.plaf.basic.BasicArrowButton(SwingConstants.SOUTH);
        btnZup = new javax.swing.plaf.basic.BasicArrowButton(SwingConstants.NORTH);
        stepsizeLabel = new javax.swing.JLabel();
        xStepSzLabel = new javax.swing.JLabel();
        yStepSzLabel = new javax.swing.JLabel();
        zStepSzLabel = new javax.swing.JLabel();
        txt_xStepSz = new javax.swing.JTextField();
        txt_yStepSz = new javax.swing.JTextField();
        txt_zStepSz = new javax.swing.JTextField();
        radBtnMvinSlice = new javax.swing.JRadioButton();
        radBtnMvSel3DRoi = new javax.swing.JRadioButton();
        radBtnMvAll = new javax.swing.JRadioButton();
        panCurrPos = new javax.swing.JPanel();
        yCurPosLabel = new javax.swing.JLabel();
        txt_yShiftTot = new javax.swing.JTextField();
        txt_zShiftTot = new javax.swing.JTextField();
        txt_xShiftTot = new javax.swing.JTextField();
        xCurPosLabel = new javax.swing.JLabel();
        zCurPosLabel = new javax.swing.JLabel();
        guiSettingsWindow = new javax.swing.JFrame();
        guiSettingsTab = new javax.swing.JTabbedPane();
        guireCtrProperties = new javax.swing.JPanel();
        JTxtBox_MovRes = new javax.swing.JTextField();
        jLabel13 = new javax.swing.JLabel();
        JTextBox_CalRes = new javax.swing.JTextField();
        jLabel14 = new javax.swing.JLabel();
        JTxtBox_MaxIter = new javax.swing.JTextField();
        jLabel15 = new javax.swing.JLabel();
        JTxtBox_CLimit = new javax.swing.JTextField();
        jLabel16 = new javax.swing.JLabel();
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
        resizeExisitingChkBox = new javax.swing.JCheckBox();
        jCheckBox2 = new javax.swing.JCheckBox();
        guiSettingsOkBtn = new javax.swing.JButton();
        guiSettingsCancelBtn = new javax.swing.JButton();
        btnGrp_2D_OR_3D_addOnClk = new javax.swing.ButtonGroup();
        typeOfMovement = new javax.swing.ButtonGroup();
        object2OperateOn = new javax.swing.ButtonGroup();
        setBgdDialog = new javax.swing.JDialog();
        txt_bgdValue = new javax.swing.JTextField();
        jLabel12 = new javax.swing.JLabel();
        btn_OksetBgd = new javax.swing.JButton();
        btn_cancelSetBgd = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        mv2Manager = new javax.swing.JButton();
        addto3Dlist = new javax.swing.JButton();
        remove2Dfrom3D = new javax.swing.JButton();
        transferfromManager = new javax.swing.JButton();
        recenterIn2D = new javax.swing.JButton();
        ResetPaths = new javax.swing.JButton();
        buttonExit = new javax.swing.JButton();
        panel_3DBtns_ChkBox = new javax.swing.JPanel();
        AddOnClick = new javax.swing.JCheckBox();
        btnRecenter3D = new javax.swing.JButton();
        btnReload3DRois = new javax.swing.JButton();
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
        jButtonReloadSubsetRois3D = new javax.swing.JButton();
        btnClearOutside = new javax.swing.JButton();
        scrlPane_3D_RoiLst = new javax.swing.JScrollPane();
        gui3DRoiList = new javax.swing.JList<>();
        scrlPane_2D_RoiLst = new javax.swing.JScrollPane();
        gui2DRoiList = new javax.swing.JList<>();
        btnClearAll2D = new javax.swing.JButton();
        showAllRois = new javax.swing.JCheckBox();
        radBtnshowRT = new javax.swing.JCheckBox();
        ckBxRectrForMeasurement = new javax.swing.JCheckBox();
        roi2D_from_xy_ordinates = new javax.swing.JButton();

        TranslateRoi.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        TranslateRoi.setTitle("Translate Rois");
        TranslateRoi.setLocationByPlatform(true);
        TranslateRoi.setMinimumSize(new java.awt.Dimension(480, 600));
        TranslateRoi.setName("frameTransRoi"); // NOI18N
        TranslateRoi.setResizable(false);

        closeButton.setText("Close");
        closeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                closeButtonActionPerformed(evt);
            }
        });

        labelTitle.setText("ROI Translator");

        panelMvbyPix.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createEtchedBorder(), "Move by Pixels", javax.swing.border.TitledBorder.CENTER, javax.swing.border.TitledBorder.DEFAULT_POSITION));

        txt_yDist.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        txt_yDist.setText("0");

        txt_xDist.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        txt_xDist.setText("0");

        txt_zDist.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        txt_zDist.setText("0");

        xPosLabel.setText("x Position");

        zPosLabel.setText("z Position");

        yPosLabel.setText("y Position");

        btnMove.setText("Move");
        btnMove.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnMoveActionPerformed(evt);
            }
        });

        typeOfMovement.add(radBtn_RelativeMove);
        radBtn_RelativeMove.setSelected(true);
        radBtn_RelativeMove.setText("Relative");

        typeOfMovement.add(radBtn_AbsMove);
        radBtn_AbsMove.setText("Absolute");

        javax.swing.GroupLayout panelMvbyPixLayout = new javax.swing.GroupLayout(panelMvbyPix);
        panelMvbyPix.setLayout(panelMvbyPixLayout);
        panelMvbyPixLayout.setHorizontalGroup(
            panelMvbyPixLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelMvbyPixLayout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(panelMvbyPixLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(radBtn_RelativeMove)
                    .addGroup(panelMvbyPixLayout.createSequentialGroup()
                        .addGroup(panelMvbyPixLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(xPosLabel)
                            .addComponent(zPosLabel)
                            .addComponent(yPosLabel))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(panelMvbyPixLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(txt_zDist)
                            .addComponent(txt_yDist, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(txt_xDist, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 55, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addGroup(panelMvbyPixLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                        .addComponent(btnMove)
                        .addComponent(radBtn_AbsMove)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        panelMvbyPixLayout.setVerticalGroup(
            panelMvbyPixLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, panelMvbyPixLayout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(panelMvbyPixLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txt_xDist, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(xPosLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(panelMvbyPixLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txt_yDist, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(yPosLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(panelMvbyPixLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txt_zDist, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(zPosLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(radBtn_RelativeMove)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(radBtn_AbsMove)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(btnMove)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        panelClick2Move.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createEtchedBorder(), "Click to Move ", javax.swing.border.TitledBorder.CENTER, javax.swing.border.TitledBorder.DEFAULT_POSITION));

        btnNorth.setText("");
        btnNorth.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnNorthActionPerformed(evt);
            }
        });

        btnEast.setText("");
        btnEast.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnEastActionPerformed(evt);
            }
        });

        btnWest.setText("");
        btnWest.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnWestActionPerformed(evt);
            }
        });

        btnSouth.setText("");
        btnSouth.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSouthActionPerformed(evt);
            }
        });

        btnResetinMove.setFont(new java.awt.Font("Times New Roman", 1, 24)); // NOI18N
        btnResetinMove.setForeground(new java.awt.Color(255, 0, 0));
        btnResetinMove.setText("R");
        btnResetinMove.setToolTipText("Click to Reset the Movement");
        btnResetinMove.setBorder(new javax.swing.border.SoftBevelBorder(javax.swing.border.BevelBorder.RAISED));
        btnResetinMove.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnResetinMoveActionPerformed(evt);
            }
        });

        btnZdn.setText("");
        btnZdn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnZdnActionPerformed(evt);
            }
        });

        btnZup.setText("");
        btnZup.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnZupActionPerformed(evt);
            }
        });

        stepsizeLabel.setText("Step Size (Pixels):");

        xStepSzLabel.setText("x");

        yStepSzLabel.setText("y");

        zStepSzLabel.setText("z");

        txt_xStepSz.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        txt_xStepSz.setText("10");

        txt_yStepSz.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        txt_yStepSz.setText("10");

        txt_zStepSz.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        txt_zStepSz.setText("10");

        object2OperateOn.add(radBtnMvinSlice);
        radBtnMvinSlice.setText("Move all ROI(2D) in a Slice");
        radBtnMvinSlice.setEnabled(false);

        object2OperateOn.add(radBtnMvSel3DRoi);
        radBtnMvSel3DRoi.setText("Move the selected 3D Roi");

        object2OperateOn.add(radBtnMvAll);
        radBtnMvAll.setSelected(true);
        radBtnMvAll.setText("Move all the 3D Rois");
        radBtnMvAll.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                radBtnMvAllStateChanged(evt);
            }
        });

        javax.swing.GroupLayout panelClick2MoveLayout = new javax.swing.GroupLayout(panelClick2Move);
        panelClick2Move.setLayout(panelClick2MoveLayout);
        panelClick2MoveLayout.setHorizontalGroup(
            panelClick2MoveLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelClick2MoveLayout.createSequentialGroup()
                .addGroup(panelClick2MoveLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(panelClick2MoveLayout.createSequentialGroup()
                        .addComponent(stepsizeLabel)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, panelClick2MoveLayout.createSequentialGroup()
                        .addGap(0, 12, Short.MAX_VALUE)
                        .addGroup(panelClick2MoveLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(radBtnMvSel3DRoi)
                            .addComponent(radBtnMvinSlice)
                            .addComponent(radBtnMvAll)
                            .addGroup(panelClick2MoveLayout.createSequentialGroup()
                                .addGap(17, 17, 17)
                                .addGroup(panelClick2MoveLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(txt_xStepSz, javax.swing.GroupLayout.PREFERRED_SIZE, 31, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addGroup(panelClick2MoveLayout.createSequentialGroup()
                                        .addGap(12, 12, 12)
                                        .addGroup(panelClick2MoveLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                            .addComponent(txt_yStepSz, javax.swing.GroupLayout.PREFERRED_SIZE, 31, javax.swing.GroupLayout.PREFERRED_SIZE)
                                            .addGroup(panelClick2MoveLayout.createSequentialGroup()
                                                .addComponent(xStepSzLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 19, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                .addGap(45, 45, 45)
                                                .addComponent(yStepSzLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 19, javax.swing.GroupLayout.PREFERRED_SIZE))))))
                            .addGroup(panelClick2MoveLayout.createSequentialGroup()
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 4, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(btnWest, javax.swing.GroupLayout.PREFERRED_SIZE, 19, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(panelClick2MoveLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(btnResetinMove, javax.swing.GroupLayout.PREFERRED_SIZE, 38, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(btnNorth, javax.swing.GroupLayout.PREFERRED_SIZE, 17, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(btnSouth, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addGap(7, 7, 7)
                                .addComponent(btnEast, javax.swing.GroupLayout.PREFERRED_SIZE, 22, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(panelClick2MoveLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(panelClick2MoveLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                        .addComponent(txt_zStepSz, javax.swing.GroupLayout.PREFERRED_SIZE, 31, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(zStepSzLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 19, javax.swing.GroupLayout.PREFERRED_SIZE))
                                    .addComponent(btnZup, javax.swing.GroupLayout.PREFERRED_SIZE, 17, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(btnZdn, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE))))))
                .addContainerGap())
        );

        panelClick2MoveLayout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {btnEast, btnNorth, btnResetinMove, btnSouth, btnWest, btnZdn, btnZup});

        panelClick2MoveLayout.setVerticalGroup(
            panelClick2MoveLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelClick2MoveLayout.createSequentialGroup()
                .addGap(4, 4, 4)
                .addComponent(stepsizeLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(panelClick2MoveLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(xStepSzLabel)
                    .addComponent(yStepSzLabel)
                    .addComponent(zStepSzLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(panelClick2MoveLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(txt_xStepSz, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(txt_yStepSz, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(txt_zStepSz, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGroup(panelClick2MoveLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(panelClick2MoveLayout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnNorth, javax.swing.GroupLayout.PREFERRED_SIZE, 13, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(panelClick2MoveLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(btnResetinMove, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(btnEast)
                            .addComponent(btnWest))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnSouth))
                    .addGroup(panelClick2MoveLayout.createSequentialGroup()
                        .addGap(27, 27, 27)
                        .addComponent(btnZup, javax.swing.GroupLayout.PREFERRED_SIZE, 13, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnZdn)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(radBtnMvinSlice)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(radBtnMvSel3DRoi)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(radBtnMvAll)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        panelClick2MoveLayout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {btnEast, btnNorth, btnResetinMove, btnSouth, btnWest, btnZdn, btnZup});

        panCurrPos.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createEtchedBorder(), "Total Movement So Far", javax.swing.border.TitledBorder.LEFT, javax.swing.border.TitledBorder.DEFAULT_POSITION));

        yCurPosLabel.setText("y");

        txt_yShiftTot.setEditable(false);
        txt_yShiftTot.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        txt_yShiftTot.setText("0");

        txt_zShiftTot.setEditable(false);
        txt_zShiftTot.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        txt_zShiftTot.setText("0");
        txt_zShiftTot.setToolTipText("");

        txt_xShiftTot.setEditable(false);
        txt_xShiftTot.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        txt_xShiftTot.setText("0");

        xCurPosLabel.setText("x");

        zCurPosLabel.setText("z");

        javax.swing.GroupLayout panCurrPosLayout = new javax.swing.GroupLayout(panCurrPos);
        panCurrPos.setLayout(panCurrPosLayout);
        panCurrPosLayout.setHorizontalGroup(
            panCurrPosLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panCurrPosLayout.createSequentialGroup()
                .addGap(18, 18, 18)
                .addGroup(panCurrPosLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(panCurrPosLayout.createSequentialGroup()
                        .addGap(33, 33, 33)
                        .addComponent(xCurPosLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(yCurPosLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(135, 135, 135)
                        .addComponent(zCurPosLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 19, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(52, 52, 52))
                    .addGroup(panCurrPosLayout.createSequentialGroup()
                        .addComponent(txt_xShiftTot, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(txt_yShiftTot, javax.swing.GroupLayout.PREFERRED_SIZE, 80, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(txt_zShiftTot, javax.swing.GroupLayout.PREFERRED_SIZE, 80, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(26, 26, 26))))
        );

        panCurrPosLayout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {txt_xShiftTot, txt_zShiftTot});

        panCurrPosLayout.setVerticalGroup(
            panCurrPosLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panCurrPosLayout.createSequentialGroup()
                .addGroup(panCurrPosLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(xCurPosLabel)
                    .addComponent(yCurPosLabel)
                    .addComponent(zCurPosLabel))
                .addGap(5, 5, 5)
                .addGroup(panCurrPosLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(txt_yShiftTot, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(txt_xShiftTot, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(txt_zShiftTot, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(25, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout TranslateRoiLayout = new javax.swing.GroupLayout(TranslateRoi.getContentPane());
        TranslateRoi.getContentPane().setLayout(TranslateRoiLayout);
        TranslateRoiLayout.setHorizontalGroup(
            TranslateRoiLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, TranslateRoiLayout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(labelTitle)
                .addGap(190, 190, 190))
            .addGroup(TranslateRoiLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(TranslateRoiLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(TranslateRoiLayout.createSequentialGroup()
                        .addComponent(panelClick2Move, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addGroup(TranslateRoiLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(panelMvbyPix, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(closeButton, javax.swing.GroupLayout.PREFERRED_SIZE, 138, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(0, 2, Short.MAX_VALUE))
                    .addComponent(panCurrPos, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        TranslateRoiLayout.setVerticalGroup(
            TranslateRoiLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(TranslateRoiLayout.createSequentialGroup()
                .addGap(6, 6, 6)
                .addComponent(labelTitle)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(panCurrPos, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(TranslateRoiLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(TranslateRoiLayout.createSequentialGroup()
                        .addComponent(panelMvbyPix, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(closeButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(TranslateRoiLayout.createSequentialGroup()
                        .addComponent(panelClick2Move, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addContainerGap(30, Short.MAX_VALUE))))
        );

        guiSettingsWindow.setTitle("Settings");
        guiSettingsWindow.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        guiSettingsWindow.setLocationByPlatform(true);
        guiSettingsWindow.setMinimumSize(new java.awt.Dimension(500, 550));
        guiSettingsWindow.setName("Settings Tab"); // NOI18N

        JTxtBox_MovRes.setText("0.25");
        JTxtBox_MovRes.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                JTxtBox_MovResActionPerformed(evt);
            }
        });

        jLabel13.setText("Movement Threshold");

        JTextBox_CalRes.setText("0.5");

        jLabel14.setText("Resize by (before recentering)");

        JTxtBox_MaxIter.setText("20");

        jLabel15.setText("Maximum Iterations");

        JTxtBox_CLimit.setText("0.1");

        jLabel16.setText("Convergence Limit (Fractional movement)");

        javax.swing.GroupLayout guireCtrPropertiesLayout = new javax.swing.GroupLayout(guireCtrProperties);
        guireCtrProperties.setLayout(guireCtrPropertiesLayout);
        guireCtrPropertiesLayout.setHorizontalGroup(
            guireCtrPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, guireCtrPropertiesLayout.createSequentialGroup()
                .addGap(88, 88, 88)
                .addGroup(guireCtrPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel14, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabel13, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(guireCtrPropertiesLayout.createSequentialGroup()
                        .addComponent(jLabel15, javax.swing.GroupLayout.PREFERRED_SIZE, 155, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(jLabel16, javax.swing.GroupLayout.DEFAULT_SIZE, 226, Short.MAX_VALUE))
                .addGap(18, 18, 18)
                .addGroup(guireCtrPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(JTxtBox_MaxIter, javax.swing.GroupLayout.PREFERRED_SIZE, 54, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(JTextBox_CalRes, javax.swing.GroupLayout.PREFERRED_SIZE, 54, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(JTxtBox_MovRes, javax.swing.GroupLayout.PREFERRED_SIZE, 54, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(JTxtBox_CLimit, javax.swing.GroupLayout.PREFERRED_SIZE, 54, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(75, 75, 75))
        );
        guireCtrPropertiesLayout.setVerticalGroup(
            guireCtrPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(guireCtrPropertiesLayout.createSequentialGroup()
                .addGap(57, 57, 57)
                .addGroup(guireCtrPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jLabel13)
                    .addComponent(JTxtBox_MovRes, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(guireCtrPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(JTextBox_CalRes, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel14))
                .addGap(18, 18, 18)
                .addGroup(guireCtrPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel15, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(JTxtBox_MaxIter, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(guireCtrPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(JTxtBox_CLimit, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel16))
                .addContainerGap(226, Short.MAX_VALUE))
        );

        guiSettingsTab.addTab("Recenter Properties", guireCtrProperties);

        guiautoROIProperties.setPreferredSize(new java.awt.Dimension(461, 354));

        guiroiPrefix.setText("ROI_");

        jLabel3.setText("Roi 2D Prefix");

        gui3DDepth.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        gui3DDepth.setText("23");
        gui3DDepth.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                gui3DDepthActionPerformed(evt);
            }
        });

        jLabel4.setText("3D Roi Depth (nslices)");

        guiroiWidth.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        guiroiWidth.setText("17");

        guiroiHeight.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        guiroiHeight.setText("17");

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

        resizeExisitingChkBox.setText("Resize existing Rois in Z");
        resizeExisitingChkBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                resizeExisitingChkBoxActionPerformed(evt);
            }
        });

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
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGroup(guiautoROIPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(gui3DRoiStartNumber, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(gui3DroiPrefix, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 51, Short.MAX_VALUE)
                            .addComponent(guiroiPrefix, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 51, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addGroup(guiautoROIPropertiesLayout.createSequentialGroup()
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGroup(guiautoROIPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(gui3DDepth, javax.swing.GroupLayout.PREFERRED_SIZE, 51, javax.swing.GroupLayout.PREFERRED_SIZE)
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
                        .addComponent(resizeExisitingChkBox)))
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
                .addComponent(resizeExisitingChkBox)
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
            .addComponent(guiSettingsTab)
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
                .addComponent(guiSettingsTab)
                .addGap(18, 18, 18)
                .addGroup(guiSettingsWindowLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(guiSettingsCancelBtn)
                    .addComponent(guiSettingsOkBtn))
                .addContainerGap())
        );

        guiSettingsWindowLayout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {guiSettingsCancelBtn, guiSettingsOkBtn});

        txt_bgdValue.setText("0");

        jLabel12.setText("Set Background");

        btn_OksetBgd.setText("Ok");
        btn_OksetBgd.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btn_OksetBgdActionPerformed(evt);
            }
        });

        btn_cancelSetBgd.setText("Cancel");
        btn_cancelSetBgd.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btn_cancelSetBgdActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout setBgdDialogLayout = new javax.swing.GroupLayout(setBgdDialog.getContentPane());
        setBgdDialog.getContentPane().setLayout(setBgdDialogLayout);
        setBgdDialogLayout.setHorizontalGroup(
            setBgdDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(setBgdDialogLayout.createSequentialGroup()
                .addGap(38, 38, 38)
                .addGroup(setBgdDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(setBgdDialogLayout.createSequentialGroup()
                        .addComponent(btn_OksetBgd)
                        .addGap(36, 36, 36)
                        .addComponent(btn_cancelSetBgd))
                    .addGroup(setBgdDialogLayout.createSequentialGroup()
                        .addComponent(jLabel12)
                        .addGap(35, 35, 35)
                        .addComponent(txt_bgdValue, javax.swing.GroupLayout.PREFERRED_SIZE, 52, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(60, Short.MAX_VALUE))
        );
        setBgdDialogLayout.setVerticalGroup(
            setBgdDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(setBgdDialogLayout.createSequentialGroup()
                .addGap(55, 55, 55)
                .addGroup(setBgdDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txt_bgdValue, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel12))
                .addGap(31, 31, 31)
                .addGroup(setBgdDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btn_OksetBgd)
                    .addComponent(btn_cancelSetBgd))
                .addContainerGap(45, Short.MAX_VALUE))
        );

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

        ResetPaths.setText("Reset Paths/Directories");
        ResetPaths.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ResetPathsActionPerformed(evt);
            }
        });

        buttonExit.setText("Done !");
        buttonExit.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonExitActionPerformed(evt);
            }
        });

        panel_3DBtns_ChkBox.setBorder(javax.swing.BorderFactory.createEtchedBorder());

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

        btnReload3DRois.setText("Reload 3D Rois");
        btnReload3DRois.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnReload3DRoisActionPerformed(evt);
            }
        });

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

        btnGenGauInt.setText("Translate Rois ");
        btnGenGauInt.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnGenGauIntActionPerformed(evt);
            }
        });

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

        jButtonReloadSubsetRois3D.setText("Load Subset 3D Roi(s)");
        jButtonReloadSubsetRois3D.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonReloadSubsetRois3DActionPerformed(evt);
            }
        });

        btnClearOutside.setText("Clear Outside ROIs");
        btnClearOutside.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnClearOutsideActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout panel_3DBtns_ChkBoxLayout = new javax.swing.GroupLayout(panel_3DBtns_ChkBox);
        panel_3DBtns_ChkBox.setLayout(panel_3DBtns_ChkBoxLayout);
        panel_3DBtns_ChkBoxLayout.setHorizontalGroup(
            panel_3DBtns_ChkBoxLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_3DBtns_ChkBoxLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(panel_3DBtns_ChkBoxLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jSep_ChkBox_Btn, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(panel_3DBtns_ChkBoxLayout.createSequentialGroup()
                        .addGroup(panel_3DBtns_ChkBoxLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(buttonAutoRoi, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(btnSetBackGround, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(make3Dbutton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(zRecenter, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(btnRecenter3D, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(btnMeasure3D, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(btnSetMeasurements, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(btnDetOverlap, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(btnReload3DRois, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addGroup(panel_3DBtns_ChkBoxLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, panel_3DBtns_ChkBoxLayout.createSequentialGroup()
                                    .addGap(43, 43, 43)
                                    .addGroup(panel_3DBtns_ChkBoxLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                        .addComponent(add2D_rad_btn)
                                        .addComponent(add3D_rad_btn)))
                                .addGroup(panel_3DBtns_ChkBoxLayout.createSequentialGroup()
                                    .addGap(40, 40, 40)
                                    .addComponent(AddOnClick)))
                            .addComponent(chkBxRectrOnAdding)
                            .addComponent(jButtonReloadSubsetRois3D, javax.swing.GroupLayout.DEFAULT_SIZE, 155, Short.MAX_VALUE)
                            .addGroup(panel_3DBtns_ChkBoxLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                                .addComponent(btnDel3DRoi, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(btnOpen3DRois, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(btnSave3DRois, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(btnGenGauInt, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                            .addComponent(btnClearOutside, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );

        panel_3DBtns_ChkBoxLayout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {btnDetOverlap, btnGenGauInt, btnMeasure3D, btnRecenter3D, btnReload3DRois, btnSetBackGround, btnSetMeasurements, buttonAutoRoi, make3Dbutton, zRecenter});

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
                .addComponent(btnReload3DRois)
                .addGap(2, 2, 2)
                .addComponent(btnGenGauInt)
                .addGap(1, 1, 1)
                .addComponent(btnSave3DRois)
                .addGap(1, 1, 1)
                .addComponent(btnOpen3DRois)
                .addGap(1, 1, 1)
                .addComponent(btnDel3DRoi)
                .addGap(4, 4, 4)
                .addComponent(jButtonReloadSubsetRois3D)
                .addGap(4, 4, 4)
                .addComponent(btnClearOutside)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jSep_ChkBox_Btn, javax.swing.GroupLayout.PREFERRED_SIZE, 15, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(AddOnClick)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(add3D_rad_btn)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(add2D_rad_btn)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 20, Short.MAX_VALUE)
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

        showAllRois.setSelected(true);
        showAllRois.setText("Show 3D Rois in Slice");

        radBtnshowRT.setText("Show ROi Intensities");

        ckBxRectrForMeasurement.setText("Recenter for Measuring");

        roi2D_from_xy_ordinates.setText("2D Rois from co-ordinates");
        roi2D_from_xy_ordinates.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                roi2D_from_xy_ordinatesActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(325, 325, 325)
                        .addComponent(jLabel1)
                        .addGap(218, 218, 218)
                        .addComponent(jLabel2)
                        .addGap(0, 182, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(panel_3DBtns_ChkBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(scrlPane_3D_RoiLst, javax.swing.GroupLayout.PREFERRED_SIZE, 247, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(scrlPane_2D_RoiLst, javax.swing.GroupLayout.PREFERRED_SIZE, 228, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(103, 103, 103)))
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addComponent(buttonExit, javax.swing.GroupLayout.PREFERRED_SIZE, 102, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(30, 30, 30))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                .addComponent(mv2Manager, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(addto3Dlist, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(remove2Dfrom3D, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(transferfromManager, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(recenterIn2D, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(ResetPaths, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(btnClearAll2D, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                            .addComponent(showAllRois)
                            .addComponent(radBtnshowRT)
                            .addComponent(ckBxRectrForMeasurement))
                        .addContainerGap())
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(roi2D_from_xy_ordinates, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addContainerGap())))
        );

        layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {ResetPaths, addto3Dlist, mv2Manager, recenterIn2D, remove2Dfrom3D, transferfromManager});

        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(11, 11, 11)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(jLabel2))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(scrlPane_3D_RoiLst)
                    .addComponent(scrlPane_2D_RoiLst)
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
                        .addComponent(ResetPaths)
                        .addGap(1, 1, 1)
                        .addComponent(btnClearAll2D, javax.swing.GroupLayout.PREFERRED_SIZE, 30, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(roi2D_from_xy_ordinates, javax.swing.GroupLayout.DEFAULT_SIZE, 101, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(showAllRois, javax.swing.GroupLayout.PREFERRED_SIZE, 52, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(radBtnshowRT)
                        .addGap(18, 18, 18)
                        .addComponent(ckBxRectrForMeasurement)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(buttonExit, javax.swing.GroupLayout.PREFERRED_SIZE, 77, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(8, 8, 8))
                    .addComponent(panel_3DBtns_ChkBox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void addto3DlistActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addto3DlistActionPerformed

        int[] Idx = this.gui2DRoiList.getSelectedIndices();
        int Index3D = gui3DRoiList.getSelectedIndex();
        if (Index3D > 0 && Idx.length > 0) {
            Roi3D tmpRoi = Rois3D.get(Index3D);
            for (int id : Idx) {
                Roi roi = Rois2D.get(id);
                tmpRoi.addRoi(roi);
            }
        }
    }//GEN-LAST:event_addto3DlistActionPerformed

    private void recenterIn2DActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_recenterIn2DActionPerformed
        // TODO add your handling code here:
        currentImp = WindowManager.getCurrentImage();
        int slice = currentImp.getCurrentSlice();
        Roi[] curSliceRois = null;

        int count = 0;
        if (Rois3D != null && Rois3D.size() > 0) {
            curSliceRois = new Roi[Rois3D.size()];
            for (Roi3D tmproi : Rois3D) {
                curSliceRois[count++] = tmproi.get2DRoi(slice);
            }
        } else {

            if (!Rois2D.isEmpty()) {
                curSliceRois = new Roi[Rois2D.size()];
                for (Roi roi : Rois2D) {
                    curSliceRois[count++] = roi;
                }
            }
        }
        if (curSliceRois.length != 0) {
            recenter(currentImp, curSliceRois, slice);
        }

    }//GEN-LAST:event_recenterIn2DActionPerformed

    private void make3DbuttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_make3DbuttonActionPerformed
        // TODO add your handling code here:

        int[] selIdxs = this.gui2DRoiList.getSelectedIndices();
        if (selIdxs.length > 1) {

            this.roi3DCount++;
            String roiName = this.roi3DPrefix + "_" + roi3DCount + "_" + this.roiPrefix;

            Roi[] rois = new Roi[selIdxs.length];
            int count = 0;
            for (Integer Idx : selIdxs) {
                rois[count++] = Rois2D.get(Idx);
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
        int[] selectedIndx = gui2DRoiList.getSelectedIndices();
        if (selectedIndx.length == 0) {
            for (Roi roi : Rois2D) {
                Manager.addRoi(roi);
            }
        } else {
            for (int roiIdx : selectedIndx) {
                Manager.addRoi(this.Rois2D.get(roiIdx));
            }
        }
        // this.Manager.addRoi();


    }//GEN-LAST:event_mv2ManagerActionPerformed

    private void remove2Dfrom3DActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_remove2Dfrom3DActionPerformed
        // TODO add your handling code here:
        int[] selectedIndx = gui2DRoiList.getSelectedIndices();
        int Idx3D = gui3DRoiList.getSelectedIndex();
        if (Idx3D != -1) {
            for (int roiIdx : selectedIndx) {
                if (!this.Rois3D.get(Idx3D).remove(Rois2D.get(roiIdx))) //display a error message;
                {
                    this.removeRoi(Rois2D.get(roiIdx));
                }
            }
        }
    }//GEN-LAST:event_remove2Dfrom3DActionPerformed

    private void transferfromManagerActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_transferfromManagerActionPerformed

        Roi[] selRois = Manager.getSelectedRoisAsArray();

        for (Roi roi : selRois) {
            this.addNewRoi(roi);
        }
    }//GEN-LAST:event_transferfromManagerActionPerformed

    private void btnClearAll2DActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnClearAll2DActionPerformed
        Roi2DListModel.clear();
        Rois2D.clear();
    }//GEN-LAST:event_btnClearAll2DActionPerformed

    private void AddOnClickActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_AddOnClickActionPerformed
        // TODO add your handling code here:
        if (AddOnClick.isSelected()) {
            add3D_rad_btn.setEnabled(true);
            add2D_rad_btn.setEnabled(true);
        } else {
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

        if (Status == JFileChooser.APPROVE_OPTION) {
            File fileOut = FC.getSelectedFile();
            if (Idx == -1) {
                for (Roi3D roi : Rois3D) {
                    File fOut = new File(fileOut.getName() + File.separator + roi.getName() + ".3Dr");
                    if (fOut != null)
                        try {
                        roi3DFileSaver(fileOut, roi);
                        defaultPath = new File(fileOut.getAbsolutePath());
                    } catch (IOException ex) {
                        Logger.getLogger(TimeSeries_3D_Analyser.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            } else {

                try {
                    roi3DFileSaver(fileOut, Rois3D.get(Idx));
                } catch (IOException ex) {
                    Logger.getLogger(TimeSeries_3D_Analyser.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            // roi3DFileSaver(fileOut, tmpRoi);
        }


    }//GEN-LAST:event_btnSave3DRoisActionPerformed

    private void roi3DFileSaver(File fileOut, Roi3D tmpRoi) throws IOException {
        //File fileOut = new File();
        if (fileOut != null) {

            ZipOutputStream fOut = null;
            ZipEntry ze = new ZipEntry("");
            try {
                String fName = fileOut.getPath() + File.separator + tmpRoi.getName() + ".zip";
                System.out.print(fName);
                fOut = new ZipOutputStream(new FileOutputStream(fName));
            } catch (FileNotFoundException ex) {
                Logger.getLogger(TimeSeries_3D_Analyser.class.getName()).log(Level.SEVERE, null, ex);
            }
            Manager.reset();
            Roi[] rois = tmpRoi.getRoiSet();
            RoiEncoder encoder = new RoiEncoder(fOut);
            for (Roi roi : rois) {
                ze = new ZipEntry(roi.getName() + ".roi");
                fOut.putNextEntry(ze);
                encoder.write(roi);
                fOut.closeEntry();
            }
            fOut.close();

        }
    }

    private Roi3D roi3DFileReader(File roiFile) {
        Roi3D tmpRoi = new Roi3D();
        ZipInputStream zin;
        byte[] buffer = new byte[2048];
        ByteArrayOutputStream dataBuff;
        ZipEntry ze;
        String RoiName;
        int len = 0;

        if (roiFile != null) {

            try {
                zin = new ZipInputStream(new FileInputStream(roiFile));

                while ((ze = zin.getNextEntry()) != null) {
                    dataBuff = new ByteArrayOutputStream();
                    RoiName = ze.getName();

                    while ((len = zin.read(buffer)) > 0) {
                        dataBuff.write(buffer, 0, len);
                    }

                    dataBuff.close();
                    RoiDecoder decoder = new RoiDecoder(dataBuff.toByteArray(), RoiName);
                    Roi roi = decoder.getRoi();
                    tmpRoi.addRoi(roi);

                }
                /*ObjectInputStream Oin = new ObjectInputStream(fIn);
                tmpRoi = (Roi3D)Oin.readObject();*/

            } catch (IOException IE) {

            }
            String nameStr = roiFile.getName();
            int endIdx = nameStr.lastIndexOf(".");
            tmpRoi.setName(nameStr.substring(0, endIdx));
        }

        return tmpRoi;
    }

    private void btnOpen3DRoisActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnOpen3DRoisActionPerformed
        // TODO add your handling code here:
        //add option for opening ROI from list
        JFileChooser fileOpener = new JFileChooser(this.defaultPath);
        fileOpener.setDialogType(JFileChooser.FILES_ONLY);
        fileOpener.setMultiSelectionEnabled(true);
        int status = fileOpener.showOpenDialog(this);
        //File [] selFiles;
        Roi3D tmpRoi;
        if (Rois3D.size() == 0) {
            xShiftTotal = yShiftTotal = zShiftTotal = 0;
            this.txt_xShiftTot.setText("" + xShiftTotal);
            this.txt_yShiftTot.setText("" + yShiftTotal);
            this.txt_zShiftTot.setText("" + zShiftTotal);
        }

        if (status == JFileChooser.APPROVE_OPTION) {
            this.selFiles = fileOpener.getSelectedFiles();

            load3DRois();
        }
        if (currentImp != null)
            this.currentImp.updateAndDraw();
    }//GEN-LAST:event_btnOpen3DRoisActionPerformed

    private void load3DRois() {
        Roi3D tmpRoi;
        for (File f : selFiles) {
            tmpRoi = roi3DFileReader(f);
            this.Rois3D.add(tmpRoi); //add 3D ROI to arraylist Rois3D
            this.Roi3DListModel.addElement(tmpRoi.getName());
            this.roi3DCount++;
        }
    }

    private void buttonAutoRoiActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonAutoRoiActionPerformed
        // TODO add your handling code here:

        start3DRoiNumber = cur3DRoiNumber;
        gui3DRoiStartNumber.setText("" + roi3DCount);
        this.gui3DroiPrefix.setText(roi3DPrefix);
        this.guiroiPrefix.setText(roiPrefix);
        this.guiroiHeight.setText("" + this.roiHeight);
        this.guiroiWidth.setText("" + this.roiWidth);
        this.gui3DDepth.setText("" + this.roiDepth);
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

        if (this.resizeExisitingChkBox.isSelected()) {

            for (Roi3D cur3DRoi : Rois3D) {

                cur3DRoi.resizeInZ(roiDepth);

            }
            this.deconstruct3Dto2D(gui3DRoiList.getSelectedIndex());
        }

    }//GEN-LAST:event_guiSettingsOkBtnActionPerformed

    private void guiSettingsCancelBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_guiSettingsCancelBtnActionPerformed
        // TODO add your handling code here:
        this.guiSettingsWindow.setVisible(false);
    }//GEN-LAST:event_guiSettingsCancelBtnActionPerformed
    private void recenterInZ() {

        //ArrayList selList;
        int[] selListIdx;
        selListIdx = gui3DRoiList.getSelectedIndices();
        IJ.showStatus("Starting to recenter...");
        if (selListIdx.length == 0) {                             //if no 3D roi is selected then select all
            int listSz = gui3DRoiList.getModel().getSize();
            selListIdx = new int[listSz];
            for (int Count = 0; Count < listSz; Count++) {
                selListIdx[Count] = Count;
            }
        }
        int zConvergeLimit = 1;
        int maxIterations = 20;
        currentImp = WindowManager.getCurrentImage();
        if (currentImp == null || !currentImp.isStack()) {
            IJ.showMessage("Invalid Image or stack");
            return;
        }
        int dispSlice = currentImp.getCurrentSlice();

        for (int Idx : selListIdx) {

            Roi3D roi3D = Rois3D.get(Idx);
            recenter(roi3D);
            boolean converged = false;
            int iterations = 0;

            while (iterations++ < maxIterations && !converged) {
                //first find the maximum (intensity) ideal to allow the user to set anyone of the many parameters 
                //that imagestatistics can measure to truely havea control over how to center it. For eg. one could 
                //use integrated density instead of mean intensity.

                double maxIntensity = 0;
                double curIntensity;
                int zatmaxIntensity = 0;

                for (int sliceCount = roi3D.getStartSlice(); sliceCount < roi3D.getEndSlice(); sliceCount++) {

                    Roi sliceRoi = roi3D.get2DRoi(sliceCount);
                    //if(currentImp == null)
                    //  currentImp = WindowManager.getCurrentImage();
                    if (sliceRoi != null) {
                        currentImp.setSlice(sliceCount);
                        currentImp.setRoi(sliceRoi);
                        ImageStatistics stat = currentImp.getStatistics(ImageStatistics.MEAN);
                        curIntensity = stat.mean;
                        if (maxIntensity < curIntensity) {
                            maxIntensity = curIntensity;
                            zatmaxIntensity = sliceCount;
                        }
                    }
                }
                int zDiff = zatmaxIntensity - roi3D.getCenterZ();
                //    System.out.print("\n Iteration #: " + iterations + " maxIntensity " +maxIntensity + " z of Max : "+ zatmaxIntensity +" present diff: " +zDiff + "\n");
                if (Math.abs(zDiff) > zConvergeLimit) {
                    roi3D.repositionZ(zDiff);
                    recenter(roi3D);
                } else {
                    converged = true;
                }
            }

        }
        currentImp.setSlice(dispSlice);
        currentImp.updateAndRepaintWindow();
        IJ.showStatus("Recentering Done...");
    }
    private void btnRecenter3DActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRecenter3DActionPerformed
        // TODO add your handling code here:

        int roiCount;
        currentImp = WindowManager.getCurrentImage();
        int maxSlice = this.currentImp.getStackSize();
        int presentSlice = currentImp.getSlice();

        for (int curSlice = 1; curSlice < maxSlice; curSlice++) {
            //currentImp.setSlice(curSlice);
            Roi[] curSliceRois = new Roi[Rois3D.size()];
            roiCount = 0;
            Roi roi2D = null;
            if (Rois3D != null && Rois3D.size() > 0) {
                for (Roi3D tmproi : Rois3D) {
                    if ((roi2D = tmproi.get2DRoi(curSlice)) != null) {
                        curSliceRois[roiCount++] = roi2D;
                    } //recenter(currentImp,roi2D,curSlice);
                    else
                            ;//this 3Droi does not have its 2D roi in this slice
                }
                if (roiCount > 0) {
                    recenter(currentImp, curSliceRois, curSlice);
                }
            }

        }

        currentImp.setSlice(presentSlice);
    }//GEN-LAST:event_btnRecenter3DActionPerformed

    private void btnDel3DRoiActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnDel3DRoiActionPerformed
        // TODO add your handling code here:
        int idx = this.gui3DRoiList.getSelectedIndex();
        int len = this.gui3DRoiList.getModel().getSize();
        if (len == 0) {
            return;
        }
        if (idx == -1) {

            int confirm = JOptionPane.showConfirmDialog(this, "No 3D Rois is selected. Delete All ?");
            if (confirm == JOptionPane.OK_OPTION || confirm == JOptionPane.YES_OPTION) {
                Roi3DListModel.removeAllElements();
                Rois3D.removeAll(Rois3D);
                Roi2DListModel.clear();
                Rois2D.clear();
                roi3DCount = 0;
                this.combinedRoi = new ShapeRoi(new Roi(0, 0, 0, 0));
            }
        } else {

            //if(combinedRoi != null ){
            //ShapeRoi sr = new ShapeRoi (Rois3D.get(idx).get2DRoi(combinedRoi.getPosition()));
            //this.combinedRoi.not(sr);
            //}
            this.Roi3DListModel.remove(idx);
            this.Rois3D.remove(idx);
            Roi2DListModel.clear();
            Rois2D.clear();
            this.roi3DCount--;
            this.currentImp.updateAndDraw();

        }
    }//GEN-LAST:event_btnDel3DRoiActionPerformed

    private void buttonExitActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonExitActionPerformed
        // TODO add your handling code here:
        if (currentImp != null) {
            currentImp.setRoi((Roi) null);
        }
        this.setVisible(false);
        this.dispose();
    }//GEN-LAST:event_buttonExitActionPerformed


    private void gui3DRoiListValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_gui3DRoiListValueChanged
        // TODO add your handling code here:        
        this.deconstruct3Dto2D(gui3DRoiList.getSelectedIndex());
    }//GEN-LAST:event_gui3DRoiListValueChanged

    private void btnMeasure3DActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnMeasure3DActionPerformed
        // TODO add your handling code here:
        //this.resultsDirectory = null;
        if (this.ckBxRectrForMeasurement.isSelected()) {
            recenterInZ();
        }
        ResultsTable rt = new ResultsTable();
        currentImp = WindowManager.getCurrentImage();
        if (currentImp == null) {
            return;
        }

        int stkSize = currentImp.getStackSize();
        if (stkSize == 1) {
            return;
        }

        ImageStatistics stat;

        this.Peaks = Peaks != null ? Peaks : new ResultsTable();
        Peaks.showRowNumbers(true);
        //Peaks.show("Gaussian Peaks");

        String currentFilename = currentImp.getTitle();
        boolean newFile = !currentFilename.equalsIgnoreCase(prevFilename);

        if (newFile) {
            prevFilename = currentFilename;
            Peaks.incrementCounter();
            Peaks.addValue("FName", currentFilename);
        }
        //Peaks.incrementCounter();
        // Peaks.addValue("FName", currentFilename);

        for (int curSlice = 1; curSlice < stkSize; curSlice++) {
            rt.incrementCounter();
            currentImp.setSlice(curSlice);

            for (Roi3D tmpRoi : Rois3D) {
                Roi roi = tmpRoi.get2DRoi(curSlice);
                if (roi != null) {
                    currentImp.setRoi(roi, true);
                    stat = currentImp.getStatistics(Measurements.MEAN);
                    rt.addValue(tmpRoi.getName(), stat.mean);
                }
            }
        }
        rt.showRowNumbers(true);
        if (radBtnshowRT.isSelected()) {
            rt.show("3D Roi Mean Measurements of " + currentImp.getShortTitle());
        }

        if (true /*Gaussian Fits*/) {
            int nCols = rt.getHeadings().length;
            ResultsTable fitRes = new ResultsTable();
            ResultsTable GaussOffsetFits = new ResultsTable();

            //ResultsTable trimmedData = new ResultsTable();
            for (int count = 0; count < nCols; count++) {
                String Label = rt.getColumnHeading(count);
                double[] data = rt.getColumnAsDoubles(count);
                double[] xData = new double[data.length];
                double[] yData = new double[data.length];
                int x = 0;
                int dataCount = 0;
                for (double y : data) {
                    x++;
                    if (y != 0.0) {
                        xData[dataCount] = x;
                        yData[dataCount] = y;
                        //System.out.printf("%.1f\t%.1f\n",xData[dataCount],yData[dataCount]);
                        dataCount++;
                    }
                }
                //dataCount--;
                xData = Arrays.copyOf(xData, dataCount);
                yData = Arrays.copyOf(yData, dataCount);
                CurveFitter fitter = new CurveFitter(xData, yData);
                fitter.doFit(CurveFitter.GAUSSIAN_NOOFFSET);

                double[] params = fitter.getParams();
                double[] params2 = new double[params.length];

                //IJ.log(fitter.getResultString() +"\n"+ " /***/ /*"+"\n" + fitter.getStatusString());
                /* fitRes.incrementCounter();
                fitRes.addLabel(Label);
                int pCount = 0;
                for(double value : params){
                    fitRes.addValue("P"+ pCount++, value);
               }
                
                fitRes.addValue("Goodness of Fit", fitter.getFitGoodness());
                fitRes.addValue("RSquared", fitter.getRSquared());
                //fitRes.show("Gaussian Fits");
                //System.arraycopy(params, 0, params2, 2, params.length-1);
                
                /**Calculation of Initial Parameters 
                 **/
                params2[0] = this.bgd != 0 ? bgd : 128;
                params2[1] = params[0];
                params2[2] = params[1];
                params2[3] = params[2];

                CurveFitter fitterOffset = new CurveFitter(xData, yData);
                fitterOffset.setInitialParameters(params2);

                fitterOffset.doFit(CurveFitter.GAUSSIAN);

                double[] params3 = fitterOffset.getParams();

                boolean bl_corr = true;         //move it to a GUI
                boolean PkFilter = true;        //move to GUI
                double minDepth = 0.6;          //move to GUI
                double maxDepth = 10;           //move to GUI

                double peak;
                if (PkFilter) {
                    peak = (bl_corr) ? params3[1] - params3[0] : params3[1];
                    if (peak > 0 /* removes negative amplitudes */ && params3[3] > minDepth && params3[3] < maxDepth) {
                        peak = (params3[1] - params3[0]);
                    } else {
                        int replacementChoice = 3; // 1 = max, 2 = min 3 = ave   move to GUI
                        double sel = 0;
                        switch (replacementChoice) {
                            case 1:
                                double max = 0;
                                for (double Curdata : yData) {
                                    max = (Curdata > max) ? Curdata : max;
                                }
                                sel = max;
                                break;
                            case 2:
                                double min = Double.MAX_VALUE;
                                for (double Curdata : yData) {
                                    min = (Curdata < min) ? Curdata : min;
                                }
                                sel = min;
                                break;
                            case 3:
                                double sum = 0;
                                for (double Curdata : yData) {
                                    sum += Curdata;
                                }
                                sel = sum / yData.length;
                                break;
                        }
                        peak = sel;
                    }
                } else {
                    peak = (bl_corr) ? params3[1] - params3[0] : params3[1];;
                }

                Peaks.addValue(Label, peak);

                GaussOffsetFits.incrementCounter();
                GaussOffsetFits.addLabel(Label);
                int p2Count = 0;
                for (double value : params3) {
                    GaussOffsetFits.addValue("P" + p2Count++, value);
                }
                p2Count = 0;
                for (double value : params2) {
                    GaussOffsetFits.addValue("Initial P" + p2Count++, value);
                }
                GaussOffsetFits.addValue("Goodness of Fit", fitterOffset.getFitGoodness());
                GaussOffsetFits.addValue("RSquared", fitterOffset.getRSquared());

            }

            String resultsTitle = "Intensity along the depth of cells in " + currentImp.getTitle().replaceAll("\\.", "_");
            //String fitResTitle = "Gaussian Fits of "+ currentImp.getTitle().replaceAll("\\.", "_");
            String GaussOffTitle = "Gaussian Fits with Offsets of " + currentImp.getTitle().replaceAll("\\.", "_");

            if (radBtnshowRT.isSelected()) {
                //fitRes.show(fitResTitle);
                GaussOffsetFits.show(GaussOffTitle);
                rt.show(resultsTitle);
            }

            if (this.resultsDirectory == null) {
                JFileChooser fc = new JFileChooser();
                fc.setDialogTitle("Choose the Directory to Save the Results");
                fc.setCurrentDirectory(defaultPath);
                fc.setDialogType(JFileChooser.SAVE_DIALOG);
                fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                fc.setApproveButtonText("Choose Directory");

                int result = fc.showSaveDialog(null);

                if (result == JFileChooser.CANCEL_OPTION) {
                    resultsDirectory = defaultPath.getAbsolutePath();
                } else {
                    resultsDirectory = fc.getSelectedFile().getAbsolutePath();
                }
            }
            GaussOffsetFits.save(resultsDirectory + File.separator + GaussOffTitle + ".csv");
            //IJ.log(""+ resultsDirectory+File.separator+GaussOffTitle+".csv"+ " saved");
            //fitRes.save(resultsDirectory+File.separator+fitResTitle+".csv");
            rt.save(resultsDirectory + File.separator + resultsTitle + ".csv");

            Peaks.showRowNumbers(true);
            Peaks.show("Gaussian Peaks with Offset Correction");

        }

    }//GEN-LAST:event_btnMeasure3DActionPerformed

    private void gui3DDepthActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_gui3DDepthActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_gui3DDepthActionPerformed

    private void btnSetBackGroundActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSetBackGroundActionPerformed
        this.btnMeasure3D.setEnabled(true);
        if (Rois2D.isEmpty()) {
            this.txt_bgdValue.setText(Double.toString(bgd));
            setBgdDialog.setVisible(true);
        } else {
            ShapeRoi combination = new ShapeRoi(Rois2D.get(1));
            ShapeRoi sr;
            for (Roi roi : Rois2D) {
                sr = new ShapeRoi(roi);
                combination.or(sr);
            }
            if (combination != null) {
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

    private void btnGenGauIntActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnGenGauIntActionPerformed
        // TODO add your handling code here:
        this.TranslateRoi.setVisible(true);
        //this.roiTranslator.setVisible(true);

    }//GEN-LAST:event_btnGenGauIntActionPerformed

    private void btnResetinMoveActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnResetinMoveActionPerformed
        // TODO add your handling code here:
        this.xShiftTotal *= -1;
        this.yShiftTotal *= -1;
        this.zShiftTotal *= -1;

        MvRois(true, this.radBtnMvAll.isSelected(), xShiftTotal, yShiftTotal, zShiftTotal);

        xShiftTotal = yShiftTotal = zShiftTotal = 0;
        this.txt_xShiftTot.setText("" + xShiftTotal);
        this.txt_yShiftTot.setText("" + yShiftTotal);
        this.txt_zShiftTot.setText("" + zShiftTotal);

        this.imageUpdated(currentImp);
        // this. btnRecenter3DActionPerformed(evt);


    }//GEN-LAST:event_btnResetinMoveActionPerformed

    private void closeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closeButtonActionPerformed
        // TODO add your handling code here:
        this.TranslateRoi.dispose();
    }//GEN-LAST:event_closeButtonActionPerformed

    private void btnNorthActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnNorthActionPerformed
        // TODO add your handling code here:
        double yStep = Double.valueOf(txt_yStepSz.getText()) * (-1);

        if (this.radBtnMvAll.isSelected()) { //allRois
            for (Roi3D roi3D : Rois3D) {
                roi3D.translateRoisXYrel(0, yStep);
            }

        } else {
            int selection = this.gui3DRoiList.getSelectedIndex();
            if (selection != -1) {
                Rois3D.get(selection).translateRoisXYrel(0, yStep);
            } else {
                javax.swing.JOptionPane.showMessageDialog(this, "Please select the 3D Roi that you want to translate or select move all rois");
            }
        }
        yShiftTotal += yStep;
        txt_yShiftTot.setText(Integer.toString(yShiftTotal));
        this.imageUpdated(currentImp);
    }//GEN-LAST:event_btnNorthActionPerformed

    private void btnSouthActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSouthActionPerformed
        // TODO add your handling code here:
        double yStep = Double.valueOf(txt_yStepSz.getText());
        if (this.radBtnMvAll.isSelected()) { //allRois
            for (Roi3D roi3D : Rois3D) {
                roi3D.translateRoisXYrel(0, yStep);
            }

        } else {
            int selection = this.gui3DRoiList.getSelectedIndex();
            if (selection != -1) {
                Rois3D.get(selection).translateRoisXYrel(0, yStep);
            } else {
                javax.swing.JOptionPane.showMessageDialog(this, "Please select the 3D Roi that you want to translate or select move all rois");
            }
        }
        yShiftTotal += yStep;
        txt_yShiftTot.setText(Integer.toString(yShiftTotal));
        this.imageUpdated(currentImp);
    }//GEN-LAST:event_btnSouthActionPerformed

    private void btnWestActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnWestActionPerformed
        // TODO add your handling code here:
        double xStep = Double.valueOf(txt_xStepSz.getText()) * (-1);
        if (this.radBtnMvAll.isSelected()) { //allRois
            Rois3D.forEach((roi3D) -> {
                roi3D.translateRoisXYrel(xStep, 0);
            });

        } else {
            int selection = this.gui3DRoiList.getSelectedIndex();
            if (selection != -1) {
                Rois3D.get(selection).translateRoisXYrel(xStep, 0);
            } else {
                javax.swing.JOptionPane.showMessageDialog(this, "Please select the 3D Roi that you want to translate or select move all rois");
            }
        }
        xShiftTotal += xStep;
        txt_xShiftTot.setText(Integer.toString(xShiftTotal));
        this.imageUpdated(currentImp);
    }//GEN-LAST:event_btnWestActionPerformed

    private void btnEastActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnEastActionPerformed
        // TODO add your handling code here:
        double xStep = Double.valueOf(txt_xStepSz.getText());
        if (radBtnMvAll.isSelected()) { //allRois
            for (Roi3D roi3D : Rois3D) {
                roi3D.translateRoisXYrel(xStep, 0);
            }

        } else {
            int selection = this.gui3DRoiList.getSelectedIndex();
            if (selection != -1) {
                Rois3D.get(selection).translateRoisXYrel(xStep, 0);
            } else {
                javax.swing.JOptionPane.showMessageDialog(this, "Please select the 3D Roi that you want to translate or select move all rois");
            }
        }
        xShiftTotal += xStep;
        txt_xShiftTot.setText(Integer.toString(xShiftTotal));
        this.imageUpdated(currentImp);
    }//GEN-LAST:event_btnEastActionPerformed

    private void btnZupActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnZupActionPerformed
        // TODO add your handling code here:
        int zStep = -1 * Integer.valueOf(txt_zStepSz.getText());
        if (radBtnMvAll.isSelected()) { //allRois
            for (Roi3D roi3D : Rois3D) {
                roi3D.repositionZ(zStep);
            }
        } else {
            int selection = this.gui3DRoiList.getSelectedIndex();
            if (selection != -1) {
                Rois3D.get(selection).repositionZ(zStep);
            } else {
                javax.swing.JOptionPane.showMessageDialog(this, "Please select the 3D Roi that you want to translate or select move all rois");
            }
        }
        zShiftTotal += zStep;
        txt_zShiftTot.setText(Integer.toString(zShiftTotal));
        this.imageUpdated(currentImp);
    }//GEN-LAST:event_btnZupActionPerformed

    private void btnZdnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnZdnActionPerformed
        // TODO add your handling code here:
        int zStep = Integer.valueOf(txt_zStepSz.getText());
        if (true) { //allRois
            for (Roi3D roi3D : Rois3D) {
                roi3D.repositionZ(zStep);
            }

        } else {
            int selection = this.gui3DRoiList.getSelectedIndex();
            if (selection != -1) {
                Rois3D.get(selection).repositionZ(zStep);
            } else {
                javax.swing.JOptionPane.showMessageDialog(this, "Please select the 3D Roi that you want to translate or select move all rois");
            }
        }
        zShiftTotal += zStep;
        txt_zShiftTot.setText(Integer.toString(zShiftTotal));
        this.imageUpdated(currentImp);
    }//GEN-LAST:event_btnZdnActionPerformed

    private void btnMoveActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnMoveActionPerformed
        // TODO add your handling code here:

        int xShift = Integer.valueOf(txt_xDist.getText());
        int yShift = Integer.valueOf(txt_yDist.getText());
        int zShift = Integer.valueOf(txt_zDist.getText());

        boolean relative = this.radBtn_RelativeMove.isSelected();
        boolean allRois = this.radBtnMvAll.isSelected();

        if (relative) {
            MvRois(relative, allRois, xShift, yShift, zShift);
            xShiftTotal += xShift;
            yShiftTotal += yShift;
            zShiftTotal += zShift;
        } else {
            if (allRois) {
                javax.swing.JOptionPane.showMessageDialog(this, "You can not move all the ROIs to same location!"
                        + "either move relatively or select an ROi");
            } else {
                int selection = this.gui3DRoiList.getSelectedIndex();
                if (selection != -1) {
                    Roi3D tmpRoi = Rois3D.get(selection);
                    tmpRoi.FindCenter();

                    int xShiftAct = tmpRoi.getCenterX() - xShift;
                    int yShiftAct = tmpRoi.getCenterY() - yShift;
                    int zShiftAct = tmpRoi.getCenterZ() - zShift;

                    Rois3D.get(selection).translateRoiXYrel(xShiftAct, yShiftAct, zShiftAct);
                    Rois3D.get(selection).repositionZ(zShiftAct);

                    xShiftTotal += xShiftAct;
                    yShiftTotal += yShiftAct;
                    zShiftTotal += zShiftAct;
                }
            }
        }

        this.imageUpdated(currentImp);
    }//GEN-LAST:event_btnMoveActionPerformed

    private void radBtnMvAllStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_radBtnMvAllStateChanged
        // TODO add your handling code here:
        xShiftTotal = 0;
        yShiftTotal = 0;
        zShiftTotal = 0;
        if (radBtnMvAll.isSelected()) {
            this.gui3DRoiList.clearSelection();         //if no selection is made then the entire list is used
        }
    }//GEN-LAST:event_radBtnMvAllStateChanged

    private void btn_OksetBgdActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btn_OksetBgdActionPerformed
        // TODO add your handling code here:
        this.bgd = Double.valueOf(this.txt_bgdValue.getText());
        this.setBgdDialog.setVisible(false);
    }//GEN-LAST:event_btn_OksetBgdActionPerformed

    private void btn_cancelSetBgdActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btn_cancelSetBgdActionPerformed
        // TODO add your handling code here:
        setBgdDialog.setVisible(false);
    }//GEN-LAST:event_btn_cancelSetBgdActionPerformed

    private void ResetPathsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ResetPathsActionPerformed
        // TODO add your handling code here:
        defaultPath = null;
        this.resultsDirectory = null;
    }//GEN-LAST:event_ResetPathsActionPerformed

    private void btnReload3DRoisActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnReload3DRoisActionPerformed
        // TODO add your handling code here:
        // int confirm = JOptionPane.showConfirmDialog(this, "No 3D Rois is selected. Delete All ?");
        // if (confirm == JOptionPane.OK_OPTION || confirm == JOptionPane.YES_OPTION){
        Roi3DListModel.removeAllElements();
        Rois3D.removeAll(Rois3D);
        roi3DCount = 0;
        this.combinedRoi = new ShapeRoi(new Roi(0, 0, 0, 0));
        //}
        xShiftTotal = yShiftTotal = zShiftTotal = 0;
        this.txt_xShiftTot.setText("" + xShiftTotal);
        this.txt_yShiftTot.setText("" + yShiftTotal);
        this.txt_zShiftTot.setText("" + zShiftTotal);

        this.load3DRois();
        this.imageUpdated(currentImp);
    }//GEN-LAST:event_btnReload3DRoisActionPerformed

    private void roi2D_from_xy_ordinatesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_roi2D_from_xy_ordinatesActionPerformed
        // TODO add your handling code here:

        File[] xy_OrdinateDatafiles = null;
        JFileChooser fc = new JFileChooser();
        FileReader dataReader = null;
        fc.setMultiSelectionEnabled(true);
        int returnStatus = fc.showOpenDialog(this);

        if (returnStatus != JFileChooser.CANCEL_OPTION) {
            /*JDialog chooseSeparator = new JDialog(this,true);
            JLabel label = new JLabel("Please choose the separator");
            String [] separatorNames = {"Tab(\t)" ,"space ","Comma"};
            
            JComboBox separatorUsed = new JComboBox(separatorNames);
            chooseSeparator.add(label);
            chooseSeparator.add(separatorUsed);
          
            chooseSeparator.pack();
            chooseSeparator.setVisible(true);
            
            
            String[] seps = {"\t"," ",","};*/
            String[] separatorNames = {"Tab(\t)", "space ", "Comma"};
            String[] seps = {"\t", " ", ","};

            String separator = (String) JOptionPane.showInputDialog(this, "Choose the separator", "Separator", JOptionPane.PLAIN_MESSAGE, null, separatorNames, separatorNames[0]);
            int choiceIndex = Arrays.binarySearch(separatorNames, separator);
            xy_OrdinateDatafiles = fc.getSelectedFiles();
            for (File xyFile : xy_OrdinateDatafiles) {
                if (xyFile != null) {
                    try {
                        dataReader = new FileReader(xyFile);
                    } catch (FileNotFoundException ex) {
                        IJ.showMessage("The file" + xyFile.getName() + " is not found");
                    }
                    String line = "";
                    float x_coord = 0, y_coord = 0;
                    BufferedReader lineReader;
                    lineReader = new BufferedReader(dataReader);
                    try {
                        while ((line = lineReader.readLine()) != null) {
                            String[] Data = line.split(seps[choiceIndex]);
                            x_coord = Float.parseFloat(Data[0]);
                            y_coord = Float.parseFloat(Data[1]);

                            OvalRoi oval = new OvalRoi(x_coord, y_coord, roiWidth, roiHeight);
                            this.addNewRoi(oval);

                        }
                    } catch (IOException ex) {
                        IJ.showMessage("Error reading data from the file " + xyFile);
                    }
                } else {
                    IJ.showMessage("Error reading files: File Chooser returned null");
                }
            }
        }

    }//GEN-LAST:event_roi2D_from_xy_ordinatesActionPerformed

    private void JTxtBox_MovResActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_JTxtBox_MovResActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_JTxtBox_MovResActionPerformed

    private void resizeExisitingChkBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resizeExisitingChkBoxActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_resizeExisitingChkBoxActionPerformed

    private void jButtonReloadSubsetRois3DActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonReloadSubsetRois3DActionPerformed
        // TODO add your handling code here:
        //Open ROIs based on a list of ROI names - ROI name corresponds to file names
        //Steps:
        //1. Open csv file with list of ROI names.
        //2. Read csv file and save list of ROI names into an array.
        //3. Open directory of where 3DROI files are saved.
        //4. Open each ROI using directory information and ROI name from array made in 2.
        //5. Use load3DRois command
        //6. Done?
//        File file = new File("C:\\demo\\demofile.txt"); //file open command

        //csv file with roi list selection
        JFileChooser jc = new JFileChooser();
//        jc.setDialogType(JFileChooser.FILES_ONLY);
        jc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        jc.setMultiSelectionEnabled(false);
        jc.showOpenDialog(this);
        File listSubset3DRois = jc.getSelectedFile();
//        System.out.println("CSV file: " + listSubset3DRois.getName());
        //read csv file into an arraylist
        ArrayList<String> list3DRois = new ArrayList<>();
        try {
            //write code to read csv file into arraylist
            FileReader fr = new FileReader(listSubset3DRois);
            try {
                String strBuff = "";
                int a = 0;
                while((a = fr.read()) != -1){
                    if(a != '\n'){
                        strBuff += (char)a;
                    } else{
                        //System.out.println("strBuff roi name: " + strBuff);
                        strBuff += " ";
                        strBuff = strBuff.trim();
                        list3DRois.add(strBuff);
                        strBuff = "";
                    }
                }
//                System.out.println("read file attempt done");
            } catch (IOException ex) {
                Logger.getLogger(TimeSeries_3D_Analyser.class.getName()).log(Level.SEVERE, null, ex);
                System.out.println("Error at fr.read() level");
            }
        } catch (FileNotFoundException ex) {
            Logger.getLogger(TimeSeries_3D_Analyser.class.getName()).log(Level.SEVERE, null, ex);
            System.out.println("Error at fr new level");
        }

        //directory selection
        jc = new JFileChooser();
        jc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        jc.showOpenDialog(this);
        File dir3DRois = jc.getSelectedFile();
        System.out.println("Directory absol path: " + dir3DRois.getAbsolutePath());
        //loop over arraylist
        selFiles = new File[list3DRois.size()];
        File file;
        System.out.println("3Droi list size: " + list3DRois.size());
        String fileName = "";
        
        for (int i = 0; i < list3DRois.size(); i++) {
            fileName = dir3DRois.getAbsolutePath().concat(File.separator) + list3DRois.get(i)+ ".zip";
                 
            System.out.println( fileName);
            file = new File(fileName); //check if .zip extension is correct
            selFiles[i] = file;
            }
        //clear all 3D roi lists
        Roi3DListModel.removeAllElements();
        Rois3D.removeAll(Rois3D);
        Roi2DListModel.clear();
        Rois2D.clear();
        roi3DCount = 0;
        this.combinedRoi = new ShapeRoi(new Roi(0, 0, 0, 0));
        //call load3DRois method to load required subset of 3D ROIs
        this.load3DRois();
        
    }//GEN-LAST:event_jButtonReloadSubsetRois3DActionPerformed

    private void btnClearOutsideActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnClearOutsideActionPerformed
        // TODO add your handling code here:
        int roiCount;
        currentImp = WindowManager.getCurrentImage();
        int maxSlice = this.currentImp.getStackSize();
        int presentSlice = currentImp.getSlice();

        for (int curSlice = 1; curSlice < maxSlice; curSlice++) {
            //currentImp.setSlice(curSlice);
            //Roi[] curSliceRois = new Roi[Rois3D.size()];
            roiCount = 0;
            Roi roi2D;
            ShapeRoi allRoisInslice = null;
            if (Rois3D != null && Rois3D.size() > 0) {
                for (Roi3D tmproi : Rois3D) {
                    if ((roi2D = tmproi.get2DRoi(curSlice)) != null) {
                       allRoisInslice = (allRoisInslice == null ) ? new ShapeRoi(roi2D) : allRoisInslice.or(new ShapeRoi(roi2D));
                       roiCount++;
                    } 
                    else
                            ;//this 3Droi does not have its 2D roi in this slice
                    
                }
                //Make sure Show 3D Roi is off
                
                if (roiCount > 0) {
                    this.currentImp.setSlice(curSlice);
                    this.currentImp.setRoi(allRoisInslice); // Check setting a new ROi in ImageJ framework reomves previous ROis set on te image
                    ImageProcessor ip  = this.currSlice.getProcessor();
                
                    ip.setValue(0.0);
                    ip.fillOutside(allRoisInslice);
                    
                    //recenter(currentImp, curSliceRois, curSlice);
                 //Demonstration for combine ROi and clear outside
                  /*ShapeRoi combinedRoi = new ShapeRoi(curSliceRois[1]).or(new ShapeRoi(curSliceRois[1]));
                  //Make sure Show 3D Roi is off
                  //this.currSlice.setRoi(combinedRoi);
                  ImageProcessor ip  = this.currSlice.getProcessor();
                  ip.setValue(0.0);
                  ip.fillOutside(combinedRoi);
                  this.currentImp.repaintWindow();*/
                  
                }
            }

        }
        currentImp.setSlice(presentSlice);
        this.currentImp.repaintWindow();     
    }//GEN-LAST:event_btnClearOutsideActionPerformed

    private void MvRois(boolean relative, boolean allRois, int xShift, int yShift, int zShift) throws HeadlessException {
        if (relative) {

            if (allRois) {
                //relative and allrois
                for (Roi3D roi3D : Rois3D) {
                    roi3D.translateRoisXYrel(xShift, yShift);
                    roi3D.repositionZ(zShift);
                }
            } else {
                //relative and move only the selection
                //code for shifting the selected 3dRoi
                int selection = this.gui3DRoiList.getSelectedIndex();
                if (selection != -1) {
                    Roi3D tRoi3D = Rois3D.get(selection);
                    tRoi3D.translateRoisXYrel(xShift, yShift);
                    tRoi3D.repositionZ(zShift);
                } else {
                    javax.swing.JOptionPane.showMessageDialog(this, "No Selection is made. Select one of the ROi3D");

                }

            }

        } else {                   //it is absolute
            if (allRois) {           //allRois
                for (Roi3D roi3D : Rois3D) {
                    roi3D.translateRoisXY(xShift, yShift);
                    roi3D.FindCenter();
                    zShift = -roi3D.getCenterZ();
                    roi3D.repositionZ(zShift);
                }
            } else {
                //code for shifting the selected 3dRoi
                int selection = this.gui3DRoiList.getSelectedIndex();
                if (selection != -1) {
                    Roi3D tRoi3D = Rois3D.get(selection);
                    tRoi3D.translateRoisXY(xShift, yShift);
                    tRoi3D.FindCenter();
                    zShift -= tRoi3D.getCenterZ();
                    tRoi3D.repositionZ(zShift);
                } else {
                    javax.swing.JOptionPane.showMessageDialog(this, "No Selection is made. Select one of the ROi3D");

                }

            }

        }
    }

    public void recenter(ImagePlus imp, Roi[] rois, int sliceNo) {
        recentering = true; //started
        if (imp != null && rois != null && sliceNo > 0) {

            //imp.lock();
            imp.setSlice(sliceNo);
            imp.updateAndDraw();
            // System.out.println("The slice number is:"+ sliceNo);

            /* variables that need user inputs            */
            boolean maxMov = true;
            double movResFactor = Double.parseDouble(JTxtBox_MovRes.getText());//gui
            double totxMov;
            double totyMov;
            double movThld;

            double calReq = Double.parseDouble(JTextBox_CalRes.getText());//0.5;           //gui
            int MaxIteration = Integer.parseInt(JTxtBox_MaxIter.getText());     //gui
            double CLimit = Double.parseDouble(JTxtBox_CLimit.getText());    //gui

            ImageStatistics stat;//new ImageStatistics();
            Calibration calib = imp.getCalibration();
            double xScale = calib.pixelWidth;
            double yScale = calib.pixelHeight;
            boolean Converge;

            int New_x;
            int New_y;

            double xMovement, yMovement, distMov;
            java.awt.Rectangle Boundary;

            for (Roi orgRoi : rois) {
                distMov = 0;
                totxMov = 0;
                totyMov = 0;
                if (orgRoi == null) {
                    break;
                } else {
                    movThld = orgRoi.getFeretsDiameter() * movResFactor;
                    Boundary = orgRoi.getBounds();
                    double scaledWidth = orgRoi.getFloatWidth() * calReq;
                    double scaledHeight = orgRoi.getFloatHeight() * calReq;
                    Roi CurRoi = new Roi((Boundary.getCenterX() - (scaledWidth / 2)), (Boundary.getCenterY() - (scaledHeight / 2)), roiWidth * calReq, roiHeight * calReq);
                    //Using a rectangle Roi for estimating the center

                    Converge = false;
                    imp.setRoi(CurRoi, true);
                    //imp.updateAndDraw();
                    stat = imp.getStatistics(Measurements.CENTER_OF_MASS + Measurements.CENTROID);

                    for (int Iteration = 1; Iteration <= MaxIteration && !Converge; Iteration++) {

                        stat = imp.getStatistics(Measurements.CENTER_OF_MASS + Measurements.CENTROID); //Calculate center of Mass and Centroid; 
                        // New_x = (int) Math.round(((stat.xCenterOfMass/xScale) - (scaledWidth/2.0)));
                        //New_y = (int) Math.round(((stat.yCenterOfMass/yScale) - (scaledHeight/2.0)));
                        /*for debugging purposes*/ //System.out.printf("Recentering Started: Iteration %d Center of Mass (x,y):(%.2f,%.2f) and Centroid (x,y) is (%.1f,%.1f)\t  Fractional movement so far %f\n",Iteration,stat.xCenterOfMass,stat.yCenterOfMass, stat.xCentroid,stat.yCentroid, (distMov/movThld));
                        // Calculate movements
                        xMovement = (stat.xCentroid - stat.xCenterOfMass) / xScale;
                        yMovement = (stat.yCentroid - stat.yCenterOfMass) / yScale;
                        if (Math.abs(xMovement) < 1 && xMovement != 0 && yMovement != 0 && Math.abs(yMovement) < 1) { //Now search nearby;
                            if (Math.abs(xMovement) > Math.abs(yMovement)) {
                                New_x = (xMovement > 0) ? (int) Math.round(stat.xCentroid / xScale - (scaledWidth / 2.0) - 1) : (int) Math.round(stat.xCentroid / xScale - (scaledWidth / 2.0) + 1);
                                New_y = (int) Math.round(stat.yCentroid / yScale - (scaledHeight / 2.0));
                            } else {
                                New_y = (yMovement > 0) ? (int) Math.round(stat.yCentroid / yScale - (scaledHeight / 2.0) - 1) : (int) Math.round(stat.yCentroid / yScale - (scaledHeight / 2.0) + 1);
                                New_x = (int) Math.round(stat.xCentroid / xScale - (scaledWidth / 2.0));
                            }
                        } else {
                            New_x = (int) Math.round(((stat.xCenterOfMass / xScale) - (scaledWidth / 2.0)));
                            New_y = (int) Math.round(((stat.yCenterOfMass / yScale) - (scaledHeight / 2.0)));

                        }
                        Converge = (Math.abs(xMovement) < CLimit && Math.abs(yMovement) < CLimit);
                        if (maxMov && !Converge) {
                            totxMov += xMovement;
                            totyMov += yMovement;
                            distMov = sqrt(totxMov * totxMov + totyMov * totyMov);
                            if (distMov >= movThld) {
                                New_x -= totxMov;
                                New_y -= totyMov;
                                Converge = true;

                                IJ.log("\nTotal Movement  of " + orgRoi.getName() + " = " + distMov + "Fraction:" + distMov / movThld);
                                IJ.log("\n" + orgRoi.getName() + " moved a lot so reseting");
                            }
                        }
                        CurRoi.setLocation(New_x, New_y);
                        imp.setRoi(CurRoi, true);
                        //imp.updateAndDraw();
                    }
                    orgRoi.setLocation((stat.xCentroid - (roiWidth / 2.0)), (stat.yCentroid - (roiHeight / 2.0)));
                    //IJ.log("\nTotal Movement  of " + orgRoi.getName()+" = " + distMov);
                    //imp.setRoi(orgRoi);

                }
            }
        }

        recentering = false;  //done
    }

    @Override
    public void mouseClicked(MouseEvent me) {
        // throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.

        if (AddOnClick.isSelected()) {
            int x = me.getX();
            int y = me.getY();

            //int Width = 10;             //Need to be set in AutoRoi Properties
            //int Height = 10;            //Need to be set in AutoRoi Properties
            ImagePlus imp = WindowManager.getCurrentImage();
            if (imp != null) {
                ImageWindow Win = imp.getWindow();
                ImageCanvas canvas = Win.getCanvas();

                int offscreenX = canvas.offScreenX(x);
                int offscreenY = canvas.offScreenY(y);
                int Start_x = offscreenX - (int) (roiWidth / 2);
                int Start_y = offscreenY - (int) (roiHeight / 2);

                int z = imp.getSlice();

                if (add2D_rad_btn.isSelected()) {
                    Roi tmpRoi = new OvalRoi(Start_x, Start_y, roiWidth, roiHeight);
                    tmpRoi.setName(this.roiPrefix + "_" + Start_x + "_" + Start_y + "_" + z);
                    tmpRoi.setLocation(Start_x, Start_y);
                    tmpRoi.setPosition(z);
                    imp.setRoi(tmpRoi, true);
                    Manager.addRoi(tmpRoi);
                    //if(this.chkBxRectrOnAdding.isSelected())
                    //                   recenter(imp, tmpRoi,z);
                    //this.addNewRoi(tmpRoi);
                }
                if (add3D_rad_btn.isSelected()) {

                    int endPosition = z + roiDepth / 2;
                    int startPosition = z > roiDepth / 2 ? z - roiDepth / 2 : 0;
                    Roi3D tmp3DRoi = new Roi3D();

                    tmp3DRoi.setName(roi3DPrefix + "_" + roi3DCount++ + roiPrefix);
                    tmp3DRoi.setCenterZ(z);
                    tmp3DRoi.setnSlices(endPosition - startPosition);

                    Roi[] rois = new Roi[roiDepth];
                    for (int curPos = startPosition, count = 0; curPos < endPosition; curPos++, count++) {
                        Roi tmpRoi = new OvalRoi(Start_x, Start_y, roiWidth, roiHeight);
                        tmpRoi.setName(tmp3DRoi.getName() + "_" + Start_x + "_" + Start_y + "_" + curPos);
                        tmpRoi.setLocation(Start_x, Start_y);
                        tmpRoi.setPosition(curPos);
                        imp.setRoi(tmpRoi, true);
                        if (this.chkBxRectrOnAdding.isSelected()) {
                            recenter(imp, tmpRoi, curPos);
                        }
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
        currentImp = WindowManager.getCurrentImage() != null ? WindowManager.getCurrentImage() : null;
        this.currCanvas = (activeImage = (currentImp != null)) ? currentImp.getCanvas() : null;
        defaultPath = new File(currentImp.getFileInfo().directory);
        if (showAllRois.isSelected() && combinedRoi != null) {
            currentImp.setRoi(combinedRoi);
        }
        //combinedRoi = new ShapeRoi(new Roi(0,0,0,0));
        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void imageClosed(ImagePlus imp) {
        currentImp = WindowManager.getCurrentImage() != null ? WindowManager.getCurrentImage() : null;
        this.currCanvas = (activeImage = (currentImp != null)) ? currentImp.getCanvas() : null;
        combinedRoi = null;
        //combinedRoi = new ShapeRoi(new Roi(0,0,0,0));
        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void imageUpdated(ImagePlus imp) {
        //boolean showROis = true;
        //throw new UnsupportedOperati onException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        if (this.showAllRois.isSelected() && !recentering) {                     //recentering will be turned on and off depending on if the recentering is in progress
            Roi roi;
            int curSlice;
            int selIdx;

            combinedRoi = new ShapeRoi(new OvalRoi(0, 0, 0, 0));
            if (currentImp != null) {
                curSlice = currentImp.getSlice();
                selIdx = this.gui3DRoiList.getSelectedIndex();
                if (selIdx == -1) {
                    for (Roi3D roi3D : Rois3D) {
                        //ShapeRoi tmpSR = ((roi = roi3D.get2DRoi(curSlice)) != null) ? new ShapeRoi(roi):null;
                        roi = roi3D.get2DRoi(curSlice);
                        if (roi != null) {
                            ShapeRoi tmpSR = new ShapeRoi(roi);
                            combinedRoi.or(tmpSR);
                        }
                    }
                    currentImp.setRoi(combinedRoi);
                } else {
                    roi = Rois3D.get(selIdx).get2DRoi(currentImp.getSlice());
                    if (roi != null) {
                        combinedRoi = new ShapeRoi(roi);
                    }
                    currentImp.setRoi(combinedRoi);
                }
            }
        }else{
            currentImp.setRoi(new OvalRoi(0,0,0,0));
        }

    }


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox AddOnClick;
    private javax.swing.JTextField JTextBox_CalRes;
    private javax.swing.JTextField JTxtBox_CLimit;
    private javax.swing.JTextField JTxtBox_MaxIter;
    private javax.swing.JTextField JTxtBox_MovRes;
    private javax.swing.JButton ResetPaths;
    private javax.swing.JFrame TranslateRoi;
    private javax.swing.JRadioButton add2D_rad_btn;
    private javax.swing.JRadioButton add3D_rad_btn;
    private javax.swing.JButton addto3Dlist;
    private javax.swing.JButton btnClearAll2D;
    private javax.swing.JButton btnClearOutside;
    private javax.swing.JButton btnDel3DRoi;
    private javax.swing.JButton btnDetOverlap;
    private javax.swing.JButton btnEast;
    private javax.swing.JButton btnGenGauInt;
    private javax.swing.ButtonGroup btnGrp_2D_OR_3D_addOnClk;
    private javax.swing.JButton btnMeasure3D;
    private javax.swing.JButton btnMove;
    private javax.swing.JButton btnNorth;
    private javax.swing.JButton btnOpen3DRois;
    private javax.swing.JButton btnRecenter3D;
    private javax.swing.JButton btnReload3DRois;
    private javax.swing.JButton btnResetinMove;
    private javax.swing.JButton btnSave3DRois;
    private javax.swing.JButton btnSetBackGround;
    private javax.swing.JButton btnSetMeasurements;
    private javax.swing.JButton btnSouth;
    private javax.swing.JButton btnWest;
    private javax.swing.JButton btnZdn;
    private javax.swing.JButton btnZup;
    private javax.swing.JButton btn_OksetBgd;
    private javax.swing.JButton btn_cancelSetBgd;
    private javax.swing.JButton buttonAutoRoi;
    private javax.swing.JButton buttonExit;
    private javax.swing.JCheckBox chkBxRectrOnAdding;
    private javax.swing.JCheckBox ckBxRectrForMeasurement;
    private javax.swing.JButton closeButton;
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
    private javax.swing.JButton jButtonReloadSubsetRois3D;
    private javax.swing.JCheckBox jCheckBox2;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel14;
    private javax.swing.JLabel jLabel15;
    private javax.swing.JLabel jLabel16;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JSeparator jSep_ChkBox_Btn;
    private javax.swing.JLabel labelTitle;
    private javax.swing.JButton make3Dbutton;
    private javax.swing.JButton mv2Manager;
    private javax.swing.ButtonGroup object2OperateOn;
    private javax.swing.JPanel panCurrPos;
    private javax.swing.JPanel panelClick2Move;
    private javax.swing.JPanel panelMvbyPix;
    private javax.swing.JPanel panel_3DBtns_ChkBox;
    private javax.swing.JRadioButton radBtnMvAll;
    private javax.swing.JRadioButton radBtnMvSel3DRoi;
    private javax.swing.JRadioButton radBtnMvinSlice;
    private javax.swing.JRadioButton radBtn_AbsMove;
    private javax.swing.JRadioButton radBtn_RelativeMove;
    private javax.swing.JCheckBox radBtnshowRT;
    private javax.swing.JButton recenterIn2D;
    private javax.swing.JButton remove2Dfrom3D;
    private javax.swing.JCheckBox resizeExisitingChkBox;
    private javax.swing.JButton roi2D_from_xy_ordinates;
    private javax.swing.JScrollPane scrlPane_2D_RoiLst;
    private javax.swing.JScrollPane scrlPane_3D_RoiLst;
    private javax.swing.JDialog setBgdDialog;
    private javax.swing.JCheckBox showAllRois;
    private javax.swing.JLabel stepsizeLabel;
    private javax.swing.JButton transferfromManager;
    private javax.swing.JTextField txt_bgdValue;
    private javax.swing.JTextField txt_xDist;
    private javax.swing.JTextField txt_xShiftTot;
    private javax.swing.JTextField txt_xStepSz;
    private javax.swing.JTextField txt_yDist;
    private javax.swing.JTextField txt_yShiftTot;
    private javax.swing.JTextField txt_yStepSz;
    private javax.swing.JTextField txt_zDist;
    private javax.swing.JTextField txt_zShiftTot;
    private javax.swing.JTextField txt_zStepSz;
    private javax.swing.ButtonGroup typeOfMovement;
    private javax.swing.JLabel xCurPosLabel;
    private javax.swing.JLabel xPosLabel;
    private javax.swing.JLabel xStepSzLabel;
    private javax.swing.JLabel yCurPosLabel;
    private javax.swing.JLabel yPosLabel;
    private javax.swing.JLabel yStepSzLabel;
    private javax.swing.JLabel zCurPosLabel;
    private javax.swing.JLabel zPosLabel;
    private javax.swing.JButton zRecenter;
    private javax.swing.JLabel zStepSzLabel;
    // End of variables declaration//GEN-END:variables

    private void addNewRoi(Roi roi) {
        //throw new UnsupportedOperationException("Not supported yet.");// To change body of generated methods, choose Tools | Templates.
        if (roi != null) {
            this.Roi2DListModel.addElement(roi.getName());                  // Add the new roi to the list model which contains the data that is being displyed 
            // in the guiRoi2D List. This only adds the name of the roi
            this.Rois2D.add(roi);                                           // Ensure that the roi is added to the arraylist of the 2DRois
            //this.gui2DRoiList.setModel(Roi2DListModel);                       // Not sure if we need this but just in case update the model after addition
        } else {

        }
    }

    private void removeRoi(Roi roi) {

        boolean status = this.Rois2D.remove(roi);
        if (status == false)
            ; //Throw error message 
        else {
            this.Roi2DListModel.removeElement(roi.getName());
        }

        this.gui2DRoiList.setModel(Roi2DListModel);
    }

    @Override
    public void run() {

        while (!done) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
            }
            ImagePlus imp = WindowManager.getCurrentImage();
            if (imp != null) {
                ImageCanvas canvas = imp.getCanvas();
                if (canvas != previousCanvas) {
                    if (previousCanvas != null) {
                        previousCanvas.removeMouseListener(this);
                    }
                    canvas.addMouseListener(this);
                    previousCanvas = canvas;
                }
            } else {
                if (previousCanvas != null) {
                    previousCanvas.removeMouseListener(this);
                }
                previousCanvas = null;
            }

        }
    }
    //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.

}
