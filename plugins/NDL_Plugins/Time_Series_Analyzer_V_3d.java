
import ProgTools.MultiFileDialog;
import ProgTools.MultiSelectFrame;
import java.awt.*;
import java.io.*;
import java.awt.event.*;
import java.util.*;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.frame.*;
import java.awt.datatransfer.*;
import javax.swing.*;




/**
 *
 * @author balaji
 */
public class Time_Series_Analyzer_V_3d extends PlugInFrame implements ActionListener, MouseListener, ItemListener,
ClipboardOwner/**/, PlugIn, KeyListener/* for keyborad shortcut*/,Runnable,ImageListener{
    Panel panel;
    static Frame Instance;
    RoiManager Manager;

    /**
     *
     */
    protected double MeanIntensity[] = null;
    /**
     *
     */
    protected double Err[] = null;
    private boolean ADD = false;
    private String Names[] = {"Rectangle","Oval","FreeHand (not implemented)"};
    private int ROIType = 1;
    private int Width = 15;
    private int Height = 15;
    private Roi AutoROI = new OvalRoi(0,0,Width,Height);
    private ShapeRoi all = new ShapeRoi(AutoROI);
    private int MaxIteration = 15;
    private double CLimit = 0.1;
    private double MagCal = 0.5;
    private boolean ReCtrMean = false;
   // private boolean Label = true;
    private  ResultsTable rt;
    private PlotWindow graph;
    private java.awt.List ROIList; //= Manager.getList();
    private Hashtable Rois; // = Manager.getROIs();
    private int ROICount = 0;
    private String Prefix = "ROI";
    private Thread  thread;
    boolean done = false;
    private ImageCanvas previousCanvas = null;
    private boolean KeepPrefix = true;
    private int uiMeasure = 2; //2 for pixel average and 3 for integrated intensity;
    ImagePlus previousImp = null, processedImp = null;
    ImageStack AveStack = null ;
    java.awt.Checkbox AddOnClick, UpdateStack, persist, LiveGraph;
    private Checkbox SubStack;
    private boolean AssSlice = false;
    private String [] Command = null ;
    private MultiFileDialog FS = null;
    private ArrayList zList = new ArrayList();
    private boolean ImageJName = true;
    private Checkbox Filtered = new Checkbox("Filter Data",true);
    private Checkbox zCentered = new Checkbox("zProfile", true);
        public void lostOwnership (Clipboard clip, Transferable cont) {}    
        /**
         *
         * @param IntegratedIntensity
         */
        public void setIntegratedIntensity(boolean IntegratedIntensity){
            uiMeasure = (IntegratedIntensity) ? 3 : 2;
        }

    /**
     *
     */
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
      
   
    @Override
         public void windowClosed(WindowEvent e) {
            Instance = null;
            done = true;
            AddOnClick.setState(false);
            ROICount = 0;
            all = null;
            AutoROI = null;
            ImagePlus.removeImageListener(this);
            ImagePlus imp = WindowManager.getCurrentImage();
            //ImageWindow Win = imp.getWindow();
            if (imp == null){
                previousCanvas = null;
                super.windowClosed(e);
               return;
            }
            ImageCanvas canvas = imp.getCanvas();
            if(canvas != null){
                canvas.removeMouseListener(this);
                canvas.removeKeyListener(this);
            }
            if(previousCanvas != null)
                previousCanvas.removeMouseListener(this);
            previousCanvas = null;
          super.windowClosed(e);
        }
   
         /**
          *
          */
         @SuppressWarnings({"static-access", "static-access", "static-access"})
        public Time_Series_Analyzer_V_3d() {
        super ("Time Series V3");
        if (RoiManager.getInstance() == null){
            Manager = new RoiManager ();
        }
        else
            Manager = RoiManager.getInstance();
        
        if (Instance != null){
            Instance.toFront();
        }
        else{
            Instance = this;
            ImagePlus.addImageListener(this);
            ROIList = Manager.getList();
            Rois = Manager.getROIs();
            WindowManager.addWindow(this);
            setLayout(new FlowLayout(FlowLayout.CENTER,5,5));

            panel = new Panel();
            panel.setLayout(new GridLayout(16, 0, 0, 0));
           
            addButton("AutoROIProperties");
            addButton("Recenter");
            addButton("Recenter Parameters");
            addButton("GetAverage"); //Average over all the ROIs
            addButton("GetIntegratedIntensity");
            addButton("Reset");
            addButton("Translate ROi's");
            addButton("Generate ROI's");
            addButton("Detect Overlap");
            addButton("TestUI");
            addButton("GetGaussianIntensities");
            //addButton("SetasAutoROi");
            AddOnClick = new Checkbox("Add On Click");
            panel.add(AddOnClick);
            AddOnClick.setState(false);
            panel.add(persist = new Checkbox("Persist", false));
           // panel.add(LiveGraph = new Checkbox("Live Graph", false));
            panel.add(UpdateStack = new Checkbox("New thread for measuring", true));
            panel.add(SubStack = new Checkbox("Process substack",false));
            add(panel);
            pack();
            //GUI.center(this);
            this.setVisible(true);
            thread = new Thread(this,"Time Series ");
            thread.setPriority(Math.max(thread.getPriority()-2,Thread.MIN_PRIORITY));
            thread.start();
        }
    }
        void addButton(String label) {
		Button b = new Button(label);
		b.addActionListener(this);
		panel.add(b);
	}
        public void actionPerformed(ActionEvent e) {
		String label = e.getActionCommand();
		if (label==null)
			return;
		String command = label;
		
               
                if(command.equals("AutoROIProperties"))
                    SetAutoROIProperties();
                if(command.equals("Recenter"))
                    recenter();
                if(command.equals("Recenter Parameters"))
                    SetRecenterProp();
                if(command.equals("GetAverage"))
                    getAverage();
                if(command.equals("GetIntegratedIntensity"))
                    this.getIntegrated();
                if(command.equals("GetGaussianIntensities"))
                        this.getGaussianIntensities();
                if(command.equals("Reset")){
                    if(KeepPrefix){
                        ResetNum();
                    }
                    else{
                        RenameROIS();
                    }
                }
                if (command.equals("Detect Overlap")){
                    DetectOverlap();
                }
                if(command.equals("Translate ROi's")){
                    MoveRois();
                }
                if(command.equals("Generate ROI's")){
                    generateROI();
                }
		if(command.equals("SetasAutoROi"))
                    DefAutoROi();
                if(command.equals("TestUI")){
                    
                    FS = new MultiFileDialog(this,true);
                    //WindowManager.addWindow(FS.getFrame());
                    FS.setOpenAction(this, "Open");
                    FS.setCloseAction(this, "Close");
                    FS.setVisible(true);
                    //MultiFileDialog FS = new MultiFileDialog(this, true);
                    //text.insert(TestUI(),0 );
                }
                if(command.equals("Open")){
                    
                    if(FS != null){
                        GenericDialog gd = new GenericDialog("Display text");
                        Panel textpanel = new Panel();
                        TextArea text = new TextArea(FS.getList().getModel().toString());
                        textpanel.add(text);
                      //  text = ;
                        gd.addPanel(textpanel);
                        //textpanel.pack();
                        gd.showDialog();
                    }
                    else{
                        ij.IJ.showMessage("No  UI");
                    }
               
                }
        }
        /**
         *
         */
        protected void DefAutoROi(){
            IJ.showMessage("Yet to be Implemented");
            
        }
        
        /**
         *
         */
        protected void MoveRois(){
            GenericDialog gd = new GenericDialog("Translate ROi's");
            gd.addNumericField("Enter the y shift(negative would move the ROis up)",0,0);
            gd.addNumericField("Enter the x shift (negative would move the ROis left)",0,0);
            gd.showDialog();
            int xShift = 0, yShift = 0;
            if(!gd.wasCanceled()){
                ROIList = Manager.getList();
                Rois = Manager.getROIs();
                int indexes[] = ROIList.getSelectedIndexes();
                if(indexes.length == 0)
                    indexes = getAllIndexes(ROIList.getItemCount());
                if(indexes.length == 0)
                {
                    IJ.showMessage("No rois in the ROI manager");
                    return;
                }
                yShift = (int)gd.getNextNumber();
                xShift = (int)gd.getNextNumber();
                java.awt.Rectangle BRect ;
                Roi CurRoi,tmpRoi;
                int NewX, NewY;
                for(int i = 0 ; i < indexes.length ; i++){
                    CurRoi = (Roi)Rois.get(ROIList.getItem(indexes[i]));
                    BRect = CurRoi.getBounds();
                    NewX = Math.round(BRect.x  + xShift);
                    NewY = Math.round(BRect.y  + yShift);
                    tmpRoi = (Roi) CurRoi.clone();
                    tmpRoi.setLocation(NewX,NewY);
                    tmpRoi.setName(CurRoi.getName());
                    UpDate(tmpRoi,indexes[i]);
                }
                ROIList = Manager.getList();
                Rois = Manager.getROIs();
                showAllROIs();
                }
            
        }
        public void itemStateChanged(ItemEvent e) {
                // Want to use it for dynamically updating the profile. Will be addresssed in later version
		
	}
        public void keyPressed(KeyEvent e) {}
	public void keyReleased (KeyEvent e) {}
	public void keyTyped (KeyEvent e) {}
        public void mousePressed(MouseEvent e){}
        public void mouseReleased(MouseEvent e) {}
	public void mouseExited(MouseEvent e) {}
	public void mouseClicked(MouseEvent e) {
                
                if (AddOnClick.getState()){
                int x = e.getX();
                int y = e.getY();
                
                ImagePlus imp = WindowManager.getCurrentImage();
                if (imp != null){
                    ImageWindow Win = imp.getWindow();
                    ImageCanvas canvas = Win.getCanvas();

                    int offscreenX = canvas.offScreenX(x);
                    int offscreenY = canvas.offScreenY(y);
                    int Start_x = offscreenX - (int)(Width/2);
                    int Start_y = offscreenY - (int)(Height/2);
                    AutoROI.setLocation(Start_x, Start_y);                
                    ROICount++;
                    imp.setRoi(AutoROI);
                    String name;
                    if (ROICount < 100){
                         name = (ROICount < 10 ) ? Prefix + "00" + ROICount :  Prefix + "0"  + ROICount;       
                    }
                    else{
                        name = Prefix + ROICount;
                    }
                    ROIList.add(name);
                    Roi temp = (Roi)AutoROI.clone();
                    temp.setName(name);
                    Rois.put(name,temp); 
                    if(persist.getState()){
                        showAllROIs();
                    }
                   // if(LiveGraph.getState()){
                      //  getAverage();
                    //}
                   /* if(DisplayLabel.getState()){
                        AddLabel(temp, name);
                    }*/
                } 
            }
        }
        /**
         *
         */
        public void ResetNum(){
            ROIList = Manager.getList();
            Rois = Manager.getROIs();
            int indexes[] = ROIList.getSelectedIndexes();
            if(indexes.length == 0)
                indexes = getAllIndexes(ROIList.getItemCount());
            if(indexes.length == 0)
            {
                ROICount = 0;
                //IJ.showMessage("No rois in the ROI manager");
                return;
            }
            
                String Label = "";
                for(int i = 0 ; i < indexes.length ; i++,++ROICount){
                    Roi tmpRoi =  (Roi)Rois.get(ROIList.getItem(indexes[i]));
                    if(tmpRoi == null)
                    {
                        IJ.showMessage("Error","Encountered error while reading ROI's ");
                        return;
                    }
                  Label = tmpRoi.getName();
                  Label = Label.substring(0,(Label.length()-2));
                    if (ROICount < 100){
                         Label = (ROICount < 9 ) ? Prefix + "00" + (ROICount+1) :  Prefix + "0"  + (ROICount+1);
                    }
                    else{
                        Label = Prefix + (ROICount +1);
                    }
                    
                    //tmpRoi.setName(Label);
                    Manager.select(indexes[i]);
                    Manager.runCommand("Rename",Label);
                }
        }
        
        /**
         *
         */
        public void RenameROIS(){
            ROICount = 0;
            int indexes[] = ROIList.getSelectedIndexes();
            if(indexes.length == 0)
                indexes = getAllIndexes(ROIList.getItemCount());
            if(indexes.length == 0)
            {
                 
                //IJ.showMessage("No rois in the ROI manager");
                return;
            }
            //ImagePlus imp = WindowManager.getCurrentImage();
           // if( imp != null){
                ROIList = Manager.getList();
                Rois = Manager.getROIs();
                String Label = "";
                for(int i = 0 ; i < indexes.length ; i++,++ROICount){
                    Roi tmpRoi =  (Roi)Rois.get(ROIList.getItem(indexes[i]));
                    if(tmpRoi == null)
                    {
                        IJ.showMessage("Error","Encountered error while reading ROI's ");
                        return;
                    }
                    //String Label = tmpRoi.getName().substring(3);
                    if (ROICount < 100){
                         Label = (ROICount < 9 ) ? Prefix + "00" + (ROICount+1) :  Prefix + "0"  + (ROICount +1);
                    }
                    else{
                        Label = Prefix + (ROICount+1);
                    }
                    //tmpRoi.setName(Label);
                    Manager.select(indexes[i]);
                    Manager.runCommand("Rename",Label);
                    
                }   
            //}
        }
    /*    The following methods become irrelavant aft. the implementation of showall function in ROIManager. ShowAll is more
     *    convenient and powerfull than the following methods. Hence scrapped the following functions.*/
    
        /**
         *
         */
        public void showAllROIs(){
            int indexes[] = getAllIndexes(ROIList.getItemCount());
            if(indexes.length == 0)
            {
                IJ.showMessage("No rois in the ROI manager");
                return;
            }
            Roi temp  = (Roi)Rois.get(ROIList.getItem(indexes[0]));
            all = new ShapeRoi(temp);
            ShapeRoi CurRoi;
            Roi tmpRoi;
            for(int i = 1 ; i < indexes.length ; i++){
                tmpRoi =  (Roi)Rois.get(ROIList.getItem(indexes[i]));
                CurRoi = new ShapeRoi(tmpRoi);
                all.xor(CurRoi);
            }
            ImagePlus imp = WindowManager.getCurrentImage();
            imp.setRoi(all);
        }
	public void mouseEntered(MouseEvent e) {}
        // This method is reqd. for the button interface
        /**
         *
         */
        public void SetAutoROIProperties(){
            ij.gui.GenericDialog gd = new ij.gui.GenericDialog("AutoROI properties");
            gd.addNumericField("Width: ", Width, 0);
            gd.addNumericField("Height: ", Height, 0);
            gd.addNumericField("Start the ROI number from",ROICount, 0);
            // boolean values[] = {false,true,false};
            gd.addChoice("ROI Type",Names,Names[ROIType]);
            gd.addCheckbox("Resize exisiting ROIS", false);
            gd.addCheckbox("Keep the prefix during reset",KeepPrefix);
            gd.addCheckbox("Use ImageJ Names for generating ROIs", ImageJName);
            gd.addStringField("Prefix for AutoROI (when ImageJ Name is not used)",Prefix);
            gd.showDialog();
            
            
            if(!gd.wasCanceled())
            {
                this.Width = (int)gd.getNextNumber();
                this.Height = (int)gd.getNextNumber();
                int Count = (int)gd.getNextNumber();
                if(Count != ROICount && Count > 1) ROICount = Count - 1;              // >1 is an indication the number is last ROI in the manager
                //IJ.log("New ROI"+ Width + " "+ Height); //for debugging
                this.Prefix = gd.getNextString();
                ROIType = gd.getNextChoiceIndex();
                switch (ROIType) {
                case 0:
                        this.AutoROI = new Roi(0,0,Width,Height);
                        break;
                case 1:
                        this.AutoROI = new OvalRoi(0,0,Width,Height);
                        break;
                }
                if(gd.getNextBoolean()){
                    ResizeROIS();
                }
                KeepPrefix = gd.getNextBoolean();
                ImageJName = gd.getNextBoolean();
                
            }
            
        }
        /**
         *
         * @param Scale
         */
        public void ScaleROIS(double Scale){
                Width = (int)(Width * Scale);
                Height = (int)(Height * Scale);
                switch (ROIType) {
                case 0:
                        this.AutoROI = new Roi(0,0,Width,Height);
                        break;
                case 1:
                        this.AutoROI = new OvalRoi(0,0,Width,Height);
                        break;
                }
                ResizeROIS();
        }
        /**
         *
         * @param Width
         * @param Height
         */
        public void ScaleROIS(int Width, int Height){
                this.Width = Width ;
                this.Height = Height ;
                switch (ROIType) {
                case 0:
                        this.AutoROI = new Roi(0,0,Width,Height);
                        break;
                case 1:
                        this.AutoROI = new OvalRoi(0,0,Width,Height);
                        break;
                }
                ResizeROIS();
        }
        /**
         *
         */
        public void ResizeROIS(){
            ROIList = Manager.getList();
            Rois = Manager.getROIs();
            int indexes[] = ROIList.getSelectedIndexes();
            if(indexes.length == 0)
                indexes = getAllIndexes(ROIList.getItemCount());
            if(indexes.length == 0)
            {
                IJ.showMessage("No rois in the ROI manager");
                return;
            }
            java.awt.Rectangle BRect ;
            Roi CurRoi,tmpRoi;
            int NewX, NewY;
            for(int i = 0 ; i < indexes.length ; i++){
                CurRoi = (Roi)Rois.get(ROIList.getItem(indexes[i]));
                BRect = CurRoi.getBounds();
                NewX = Math.round(BRect.x + (BRect.width - Width)/2);
                NewY = Math.round(BRect.y + (BRect.height - Height)/2);
                tmpRoi = (Roi) AutoROI.clone();
                tmpRoi.setLocation(NewX,NewY);
                tmpRoi.setName(CurRoi.getName());
                UpDate(tmpRoi,indexes[i]);
            }
            ROIList = Manager.getList();
            showAllROIs();
        }
        /**
         *
         */
        public void SetRecenterProp(){
            ij.gui.GenericDialog gd = new ij.gui.GenericDialog("Recentering Properties");
            
            gd.addNumericField("Convergence Limit (Pixels) ", CLimit, 1);
            gd.addNumericField("Maximum Iterations: ", MaxIteration, 0);
            gd.addNumericField("Rescale ROI by ",MagCal,1);
            
            gd.addCheckbox("Recenter for measuring mean",ReCtrMean);
            gd.showDialog();
            if(!gd.wasCanceled())
            {
                CLimit = gd.getNextNumber();
                MaxIteration = (int)gd.getNextNumber();
                MagCal = gd.getNextNumber();
                ReCtrMean = (boolean) gd.getNextBoolean();
            }
        }
        /**
         *
         */
        public void recenter(){
            ImagePlus imp = WindowManager.getCurrentImage();
            if (imp == null){
                IJ.showMessage("OOPS! no image open");
             return ;   
            }
            int CurSlice =  imp.getCurrentSlice();
            recenter(imp,CurSlice);
            imp.setRoi(all);
        }
        /**
         *
         * @param imp
         * @param CurSlice
         */
        public void recenter(ImagePlus imp, int CurSlice){
        /*java.awt.List ROIList = Manager.getList();*/
            if(imp != null){
                   int indexes[] = ROIList.getSelectedIndexes();
                if(indexes.length == 0)
                    indexes = getAllIndexes(ROIList.getItemCount());
                /* ImagePlus imp = WindowManager.getCurrentImage();*/
                if(indexes.length == 0)
                {
                    IJ.showMessage("No rois in the ROI manager");
                    return;
                }
                   
                int CurROIWidth = this.Width;
                int CurROIHeight = this.Height;
                ScaleROIS(MagCal);
                
                ImageStatistics stat = new ImageStatistics();
                ij.measure.Calibration calib = imp.getCalibration();
                double xScale = calib.pixelWidth;
                double yScale = calib.pixelHeight;
                ShapeRoi temp = null;
                //all = new ShapeRoi(AutoROI);
                boolean Converge = false;
                //ShapeRoi all = new ShapeRoi(imp.getRoi());
                //all = new ShapeRoi(imp.getRoi());
                int New_x = 0;
                int New_y = 0;
                imp.setSlice(CurSlice);
                double xMovement = 0, yMovement = 0;
                java.awt.Rectangle Boundary;
                for(int i = 0; i < indexes.length ; i++){
                        //Manager.select(indexes[i]);
                        Roi tmpRoi =  (Roi)Rois.get(ROIList.getItem(indexes[i]));
                        if (AssSlice){
                           String name = ROIList.getItem(indexes[i]);
                           /*int posi = name.lastIndexOf("_");
                           String suffix = name.substring(posi+1);
                           Integer Slice = new Integer(suffix);*/

                           int sliceNo = Manager.getSliceNumber(name); //Slice.intValue();
                           if(sliceNo != -1)
                                imp.setSlice(sliceNo);
                        }
                        Roi CurRoi =  (Roi) tmpRoi.clone(); //new Roi((int)((stat.xCentroid/xScale)-(Width*MagCal)/2.0),(int)((stat.yCentroid/yScale)- (Height*MagCal)/2.0),Width*MagCal,Height*MagCal);
                        Boundary = CurRoi.getBounds();
                        Converge = false;
                        imp.setRoi(CurRoi);
                        imp.updateAndDraw();
                        double OldDiff = 0,NewDiff = 0;
                        int Old_x,Old_y;
                        for(int Iteration = 1 ; Iteration <= MaxIteration  && !Converge; Iteration++){
                            stat = imp.getStatistics(64 + 32); //Calculate center of Mass and Centroid; 
                            New_x = (int) Math.round(((stat.xCenterOfMass/xScale) - (Boundary.getWidth()/2.0)));
                            New_y = (int) Math.round(((stat.yCenterOfMass/yScale) - (Boundary.getHeight()/2.0)));
                            // Calculate movements
                            xMovement =(stat.xCentroid - stat.xCenterOfMass)/xScale;
                            yMovement = (stat.yCentroid - stat.yCenterOfMass)/yScale;
                            if( Math.abs(xMovement) < 1 && xMovement != 0 && yMovement != 0 && Math.abs(yMovement) < 1){ //Now search nearby;
                                if(Math.abs(xMovement) > Math.abs(yMovement)){
                                    New_x = (xMovement > 0) ? (int)Math.round(stat.xCentroid/xScale - (Boundary.getWidth()/2.0) - 1) : (int)Math.round(stat.xCentroid/xScale - (Boundary.getWidth()/2.0) + 1);
                                    New_y = (int) Math.round(stat.yCentroid/yScale - (Boundary.getHeight()/2.0));
                                }
                                else{
                                    New_y = (yMovement > 0) ? (int)Math.round(stat.yCentroid/yScale -(Boundary.getHeight()/2.0)- 1) : (int)Math.round(stat.yCentroid/yScale - (Boundary.getHeight()/2.0)+ 1);
                                    New_x = (int) Math.round(stat.xCentroid/xScale -(Boundary.getWidth()/2.0));
                                }
                            }
                            else{
                                New_x = (int)Math.round (((stat.xCenterOfMass/xScale) - (Boundary.getWidth()/2.0)));
                                New_y = (int)Math.round (((stat.yCenterOfMass/yScale) - (Boundary.getHeight()/2.0)));

                            }
                            Converge = ( Math.abs(xMovement) < CLimit && Math.abs(yMovement) < CLimit)  ? true : false ;
                            CurRoi.setLocation(New_x ,New_y);
                            imp.setRoi(CurRoi);
                        }
                        UpDate(CurRoi,indexes[i]);
                        temp = new ShapeRoi(CurRoi);
                        all = (i == 0) ? new ShapeRoi(CurRoi) : all.xor(temp);

                       /* if(!Converge) 
                                IJ.log(indexes[i] + "\t ROI did not converge" );*/
                       /* else
                               IJ.log(indexes[i] + "\t ROI converged" );*/

                }
               //imp.setRoi(all);
               ScaleROIS(CurROIWidth,CurROIHeight);
           
            } 
            
        }        
        /**
         *
         * @param count
         * @return
         */
        public int[] getAllIndexes(int count){
             int[] indexes = new int[count];
             for (int i=0; i<count; i++)
                indexes[i] = i;
             return indexes;
        }
        /**
         *
         * @param DispRes
         */
        public void getAveWithoutUpdate(boolean DispRes){
           
            ImagePlus imp = WindowManager.getCurrentImage();
            ROIList = Manager.getList();
            Rois = Manager.getROIs();
        boolean Skip = false;
            if (imp != null){
                int indexes[] = ROIList.getSelectedIndexes();
                if(indexes.length == 0)
                    indexes = getAllIndexes(ROIList.getItemCount());
                if(indexes.length == 0){
                    IJ.showMessage("You need to add atleast one ROI");
                    return;
                }
                if(indexes.length > 148  ){
                    String CurrentVersion = IJ.getVersion();
                   int  newVersion =  CurrentVersion.compareToIgnoreCase("1.40");
                    if (newVersion < 0){
                        Skip = true;
                        IJ.showMessage("Warning","Results table in ImageJ version 1.40 and less can  only display 150 (148 ROis) columns only. Excess "+(indexes.length - 148)+" ROis will be omitted");
                    }
                    
                }
                ImageStatistics stat = new ImageStatistics();
                int MaxSlice = imp.getStackSize(); 
                if(MaxSlice < 2){
                    IJ.showMessage("This plugin requires a ImageStack: ImageJ found" + MaxSlice + "slice only");
                    return;
                }
                int BeginSlice = 1;
                int EndSlice = MaxSlice;
                int TotSlice = MaxSlice;
                if(SubStack.getState()){
                    GenericDialog gd = new GenericDialog("Sub stack parameters");
                    gd.addNumericField("Begin at slice number",1,0);
                    gd.addNumericField("End at slice number",MaxSlice,0);
                    gd.showDialog();

                    if(gd.wasOKed()){

                        BeginSlice = (int)gd.getNextNumber();
                        EndSlice = (int)gd.getNextNumber();

                        BeginSlice = (BeginSlice >0 && BeginSlice < MaxSlice && BeginSlice < EndSlice) ? BeginSlice : 0;
                        EndSlice = (EndSlice < MaxSlice ) ? EndSlice : MaxSlice;

                        TotSlice = EndSlice - BeginSlice + 1;
                    }


                }
                MeanIntensity = new double[TotSlice];
                Err = new double[TotSlice];
                //int StartSlice = imp.getCurrentSlice();
                String Mean = "";
                double Sum, SqSum, Variance;
                Roi roi;
                rt = new ResultsTable();
                ImageProcessor ip = imp.getProcessor();
                int highlimit = (Skip) ? 147 : indexes.length;
                int nPatches = ((highlimit + 3)/ 150) + 1 ;
                for(int i = 1 ; i < nPatches; i++) rt.addColumns();
                for(int i = 0; i < highlimit; i ++){
                    roi = (Roi)Rois.get(ROIList.getItem(indexes[i]));
                    //rt.setHeading(i,"ROI" + i + "\t"); 
                    rt.setHeading(i,roi.getName()/*+"\t"*/);
                }
                 //int nCol_Res_Tab = (indexes.length > 147) ? 147 : indexes.length;
                rt.setHeading(indexes.length + 1, "Average");
                rt.setHeading(indexes.length + 2, "Err");
                double Int = 0;
                imp.unlock();
                for (int CurSlice = BeginSlice, SliceNum = 0 ; CurSlice <= EndSlice ; CurSlice ++, SliceNum++){
                    imp.setSlice(CurSlice+1);                
                    
                    Sum = 0;
                    SqSum = 0;
                    rt.incrementCounter();
                    if(ReCtrMean){
                        recenter(imp,CurSlice+1);
                        imp.setSlice(CurSlice+1);
                        imp.setRoi(all);
                    }
                   for (int CurIdx = 0; CurIdx < highlimit; CurIdx++){
                        roi = (Roi)Rois.get(ROIList.getItem(indexes[CurIdx]));
                        imp.setRoi(roi);
                        stat = imp.getStatistics(uiMeasure); // MEAN = 2
                        Int = (uiMeasure == 2) ? stat.mean : stat.mean *stat.area;
                        rt.addValue(CurIdx,Int);
                        Sum += Int;
                        SqSum += (Int * Int) ;
                    }
                    MeanIntensity[SliceNum] = Sum/indexes.length;
                    Variance = ((SqSum/indexes.length)- MeanIntensity[SliceNum]*MeanIntensity[SliceNum]);
                    Err[SliceNum] = (true /*StdErr*/) ? java.lang.Math.sqrt(Variance/indexes.length)
                                                                            : java.lang.Math.sqrt(Variance);
                    rt.addValue("Average",MeanIntensity[SliceNum]);
                    rt.addValue("Err",Err[SliceNum]);
                }
                               
                if(DispRes)
                {
                    rt.show("Time Trace(s)");
                    double [] xAxis = new double[TotSlice];
                    for(int nFrames = 1 ; nFrames <= TotSlice ; nFrames++)
                       xAxis[nFrames-1] = nFrames; 
                    Plot plot = new Plot("Time Trace Average","Time (Frames)","Average Intensity",xAxis,MeanIntensity);
                    //plot.addErrorBars(Err);
                    plot.draw();
                    if(WindowManager.getImage("Time Trace Average")== null)
                        graph = null;
                   if(graph == null){
                        graph = plot.show();
                        //graph.addErrorBars(Err);
                        graph.addPoints(xAxis,MeanIntensity,PlotWindow.CIRCLE);
                    }
                    else{
                        graph.drawPlot(plot);
                       // graph.addErrorBars(Err);
                        graph.addPoints(xAxis,MeanIntensity,PlotWindow.CIRCLE);
                    }
                }
            }
            return;
        }
  
        /**
         *
         */
    @SuppressWarnings("static-access")
        public void getAverage(){
            if(UpdateStack.getState()){
                ImagePlus imp = WindowManager.getCurrentImage();
                if(imp != null){
                    ImageStack Stack = imp.getStack();
                    if(Stack.getSize() < 2){
                       IJ.showMessage("This function requires stacks with more than 1 slice"); 
                       return;
                    }
                }   
                else{
                    IJ.showMessage("OOPS! No images are open");
                    return;
                }

                TimeTrace Trace = new TimeTrace(imp,this.Manager);
                Trace.setName("Trace");
                Trace.setPriority(Math.max(Trace.getPriority()-2,TimeTrace.MIN_PRIORITY));
                Trace.setSubStack(SubStack.getState());
                //Trace.setFiltered(true/*Filtered.getState()*/);
                //Trace.setzCentered(true/*zCentered.getState()*/);
                Trace.start();
            }
            else{
                setIntegratedIntensity(false);
                getAveWithoutUpdate(true);
            }
                
        }
    public void getGaussianIntensities(){

                double IMax =  217146 ;
                boolean AveIntensity = true;
                GenericDialog gd = new GenericDialog("Settings for Gaussian Intensity measurement");
                gd.addCheckbox("Use intensity threshold", Filtered.getState());
                gd.addCheckbox("Use Mean Pixel average (if unckd. total intensity would be used)", AveIntensity);
                gd.addNumericField("Enter the threshold for intensity", IMax, 0);
                gd.showDialog();
                Filtered.setState(gd.getNextBoolean());
                AveIntensity = gd.getNextBoolean();
                ImagePlus imp = WindowManager.getCurrentImage();
                if(imp != null){
                    ImageStack Stack = imp.getStack();
                    if(Stack.getSize() < 2){
                       IJ.showMessage("This function requires stacks with more than 1 slice");
                       return;
                    }
                }
                else{
                    IJ.showMessage("OOPS! No images are open");
                    return;
                }

                TimeTrace Trace = new TimeTrace(imp,this.Manager);
                Trace.setName("Trace");
                Trace.setPriority(Math.max(Trace.getPriority()-2,TimeTrace.MIN_PRIORITY));
                Trace.setSubStack(SubStack.getState());
                Trace.setFiltered(Filtered.getState());
                Trace.setzCentered(zCentered.getState());
                Trace.start();

    }
    /**
     *
     * @return
     */
    public double[] getAverageData(){
            return (double[])MeanIntensity.clone();
        }
        /**
         *
         */
        public void showGraph(){
            IJ.showMessage("Not yet implemented");
        }

    private void DetectOverlap() {
        //DetectOverlap instanceOverlap = new DetectOverLap();
      /*  try{
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
          }catch(Exception e){
               IJ.showMessage("Error setting system look and feel");
           }
        TestFrame test = new TestFrame();*/
    }

    private void generateROI() {
        //throw new UnsupportedOperationException("Not yet implemented");
        java.awt.FileDialog fileDialog = new FileDialog(this,"Select the text with ROI centers", FileDialog.LOAD);
        fileDialog.setVisible(true);
        String pathName = fileDialog.getDirectory();
        String fileName = fileDialog.getFile();
        //char[] cbuf = null;
        String Line = null;
        ImagePlus imp = WindowManager.getCurrentImage();
        if(imp == null) {
            IJ.showMessage("No Images open can not generate ROIs");
            return;
        }
        if(fileName != null && pathName != null){
            String path = pathName + fileName;
            //FileReader ROIFile;

            try {
              FileReader  ROIFile = new FileReader(path);
              BufferedReader fileBuff = new BufferedReader (ROIFile);
              try {                   
                    while( (Line = fileBuff.readLine()) != null){
                       String[] Data =  Line.split("\t");
                       Integer ix = new Integer(Data[0]);
                       Integer iy = new Integer(Data[1]);
                       Integer  iz = new Integer(Data[2]);
                        int Start_x = ix.intValue() - (int)(Width/2);
                        int Start_y = iy.intValue() - (int)(Height/2);
                        int Slice = iz.intValue();
                        zList.add(Slice);
                        AutoROI.setLocation(Start_x, Start_y);
                        ROICount++;
                        //imp.setRoi(AutoROI);
                        //imp.setSlice(Slice);
                        imp.setSliceWithoutUpdate(Slice);
                        Roi temp = (Roi)AutoROI.clone();

                        if(ImageJName)
                             Manager.add(imp,temp,-1);
                        else{
                            String name;
                            if (ROICount < 100){
                                 name = (ROICount < 10 ) ? Prefix + "00" + ROICount :  Prefix + "0"  + ROICount;
                            }
                            else{
                                name = Prefix + ROICount;
                            }
                            //String Suffix = "";
                                    if (AssSlice)
                                        name = name + "_" + Slice ;
                            ROIList.add(name);
//                            Roi temp = (Roi)AutoROI.clone();
                        
                            temp.setName(name);
                            Rois.put(name,temp);
                        }
                        
                       
                    }
                     if(persist.getState())
                            showAllROIs();


                   fileBuff.close();
                   ROIFile.close();
                } catch (IOException ex) {
                    IJ.showMessage("The ROI file"+ fileName+ "could not be read");
                    
                }
            } catch (FileNotFoundException ex) {
                IJ.showMessage("The ROI file"+ fileName+ "could not be  opened");
               
            }
            
        }
         return;



    }
        
        private void UpDate(Roi NewRoi, int OldIndex) {
        //throw new UnsupportedOperationException("Not yet implemented");
        String name = ROIList.getItem(OldIndex);
        Rois.remove(name);
        Rois.put(name,NewRoi);
    }

   
    @SuppressWarnings("static-access")
        private void getIntegrated() {
        //IJ.showMessage("Not yet Implemented");
            if(UpdateStack.getState()){
                ImagePlus imp = WindowManager.getCurrentImage();
                if(imp != null){
                    ImageStack Stack = imp.getStack();
                    if(Stack.getSize() < 2){
                       IJ.showMessage("This function requires stacks with more than 1 slice"); 
                       return;
                    }
                }   
                else{
                    IJ.showMessage("OOPS! No images are open");
                    return;
                }
                TimeTrace Trace = new TimeTrace(imp,this.Manager);
                Trace.setName("Trace");
                Trace.setPriority(Math.max(Trace.getPriority()-2,TimeTrace.MIN_PRIORITY));
                Trace.setTotIntensity(true);
                Trace.setSubStack(SubStack.getState());
               // Trace.setFiltered(/*Filtered.getState()*/true);
                //Trace.setzCentered(true/*zCentered.getState()*/);
                Trace.start();
            }
            else{
                this.setIntegratedIntensity(true);
                 getAveWithoutUpdate(true);
            }
               
            
    }

    /**
     *
     * @param imp
     */
    public void imageOpened(ImagePlus imp) {
    }

    /**
     *
     * @param imp
     */
    public void imageClosed(ImagePlus imp) {
        imp.getCanvas().removeMouseListener(this);
        imp.getCanvas().removeKeyListener(this);
        //
        
    }

    /**
     *
     * @param imp
     */
    public void imageUpdated(ImagePlus imp) {
        if (/*Label*/ true){
           // LabelROIs();
        }
    }

    private void TestUI() {
        
       /* MultiSelectFrame FS = new MultiSelectFrame();

        WindowManager.addWindow(FS);
        String str = FS.setOpenAction(this);
        Command[0] = new String (str);
        FS.setCloseAction(this);
        Command[1] = new String (str);
       // while (java.awt.event.AWTEventListener)

               //TrialClass TC = new TrialClass();
        //WindowManager.addWindow(Instance);
        //throw new UnsupportedOperationException("Not yet implemented");*/
    }

   /*private void GetGaussianInt() {
        throw new UnsupportedOperationException("Not yet implemented");
    }*/
    
}
