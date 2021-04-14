/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * MultiSelectFrame.java
 *
 * Created on Aug 12, 2010, 12:29:25 PM
 */
/*
 * Version 0.1
 * Added relativise method: This method will return an array of strings
 * having the portion of their path that is different from the first entry.
 *
 */
package TimeSeriesAnalyser2D;


import java.awt.event.ActionListener;
import java.io.File;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.UIManager;

/**
 * This Class brings in an ability multiple files across many directories.
 * The user browses to a given directory and then selects and adds the file of his choice to 
 * the file list. 
 * @author Balaji
 */
public class MultiSelectFrame extends javax.swing.JFrame {

    /** Creates new form MultiSelectFrame */
    public static final int OPEN = 1;
    public static final int EXIT = 0;
    public static final int NOTSHOWN = -1;

    private File[] Selection = null;
    private DefaultListModel FileListData = new DefaultListModel();
    private int result = NOTSHOWN;


    public final Object syncObj = new Object();

    /**
     *
     */
    public MultiSelectFrame() {
         try {
              UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch(Exception e) {
              System.out.println("Error setting native LAF: " + e);
            }
        initComponents();
        FileList.setModel(FileListData);
        
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
   // @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane1 = new javax.swing.JScrollPane();
        FileList = new javax.swing.JList();
        FileSelDialog = new javax.swing.JFileChooser();
        AddButton = new javax.swing.JButton();
        RemoveButton = new javax.swing.JButton();
        OpenButton = new javax.swing.JButton();
        ExitButton = new javax.swing.JButton();
        jSeparator1 = new javax.swing.JSeparator();

        setTitle("Select Multiple Files");
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosed(java.awt.event.WindowEvent evt) {
                formWindowClosed(evt);
            }
        });

        FileList.setModel(new javax.swing.AbstractListModel() {
            String[] strings = { "Item 1", "Item 2", "Item 3", "Item 4", "Item 5" };
            public int getSize() { return strings.length; }
            public Object getElementAt(int i) { return strings[i]; }
        });
        FileList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                FileListValueChanged(evt);
            }
        });
        jScrollPane1.setViewportView(FileList);

        FileSelDialog.setControlButtonsAreShown(false);
        FileSelDialog.setMultiSelectionEnabled(true);
        FileSelDialog.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                FileSelDialogActionPerformed(evt);
            }
        });

        AddButton.setText("Add to List");
        AddButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                AddButtonActionPerformed(evt);
            }
        });

        RemoveButton.setText("Remove from List");
        RemoveButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                RemoveButtonActionPerformed(evt);
            }
        });

        OpenButton.setText("Open files");
        OpenButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                OpenButtonActionPerformed(evt);
            }
        });

        ExitButton.setText("Exit ");
        ExitButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ExitButtonActionPerformed(evt);
            }
        });

        jSeparator1.setAutoscrolls(true);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                        .addComponent(FileSelDialog, javax.swing.GroupLayout.Alignment.LEADING, 0, 0, Short.MAX_VALUE)
                        .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                            .addComponent(AddButton, javax.swing.GroupLayout.PREFERRED_SIZE, 83, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGap(12, 12, 12)
                            .addComponent(RemoveButton, javax.swing.GroupLayout.PREFERRED_SIZE, 115, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                            .addComponent(OpenButton, javax.swing.GroupLayout.PREFERRED_SIZE, 133, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                            .addComponent(ExitButton, javax.swing.GroupLayout.PREFERRED_SIZE, 127, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 482, Short.MAX_VALUE)
                    .addComponent(jSeparator1, javax.swing.GroupLayout.DEFAULT_SIZE, 482, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(FileSelDialog, javax.swing.GroupLayout.PREFERRED_SIZE, 358, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, 10, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(AddButton)
                    .addComponent(RemoveButton)
                    .addComponent(OpenButton, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(ExitButton))
                .addGap(18, 18, 18)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 85, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void FileListValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_FileListValueChanged
        // TODO add your handling code here:
}//GEN-LAST:event_FileListValueChanged

    private void FileSelDialogActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_FileSelDialogActionPerformed
        // TODO add your handling code here:
}//GEN-LAST:event_FileSelDialogActionPerformed

    private void AddButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_AddButtonActionPerformed
            this.addButtonAction();
    }
    public void addButtonAction(){
        Selection =  FileSelDialog.getSelectedFiles();
        if(Selection.length != 0)
            for ( int count = 0 ; count < Selection.length ; count++ ){
                String path = new String(Selection[count].getPath());
               if(! FileListData.contains(path))
                    FileListData.addElement(path);
            }
        // TODO add your handling code here:
}//GEN-LAST:event_AddButtonActionPerformed

    private void RemoveButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_RemoveButtonActionPerformed
        if (FileList.getSelectedIndices().length != 0){
            int SelInx[] = FileList.getSelectedIndices();
            String SelValues[] = new String [SelInx.length]; //FileList.getSelectedValues().;
            for(int count = 0; count < SelInx.length ; count ++)
                SelValues[count] = (String)FileListData.get(SelInx[count]);
            for(int count = 0 ; count < SelValues.length ; count++ )
                FileListData.removeElement(SelValues[count]);
        }
        // TODO add your handling code here:
}//GEN-LAST:event_RemoveButtonActionPerformed
/**
 * This method is the event listener of open  button. Action listeners for this button need to call
 * addButtonAction() method to get the selected but not added files in the GUI. 
 *
 * @param evt
 */
    private void OpenButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_OpenButtonActionPerformed
       // this.addButtonAction();
        
        this.setVisible(false);
        this.result = MultiSelectFrame.OPEN;
        synchronized(syncObj){
            syncObj.notify();
        }
        // TODO add your handling code here:
}//GEN-LAST:event_OpenButtonActionPerformed

    private void ExitButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ExitButtonActionPerformed
       //this.setVisible(false);
        this.result = MultiSelectFrame.EXIT;
        synchronized(syncObj){
            syncObj.notify();
        }
        this.dispose();


        // TODO add your handling code here:
}//GEN-LAST:event_ExitButtonActionPerformed

    private void formWindowClosed(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosed
        result = MultiSelectFrame.EXIT;
        synchronized(syncObj){
            syncObj.notify();
        }
       this.dispose();
    }//GEN-LAST:event_formWindowClosed
    /**
     * Obatin a list of selectd files
     * @return
     */
    public JList getList(){
    return FileList;
}

/**
 *
 * @param str
 * @return
 */
public String setOpenButtonTxt(String str){
    OpenButton.setText(str);
    return str;
}
/**
 *
 * @param Instance
 * @param Cmd
 * @return
 */
public String setOpenAction(ActionListener Instance, String Cmd){
        OpenButton.addActionListener(Instance);
        OpenButton.setActionCommand(Cmd);
        return OpenButton.getActionCommand();
}
/**
 *
 * @return
 */
public String getOpenCommand() {
    return OpenButton.getActionCommand();
}
/**
 *
 * @return
 */
public String getCloseCommand() {
        return ExitButton.getActionCommand();
}
/**
 *
 * @param Instance
 * @param Cmd
 * @return
 */
public String  setCloseAction(ActionListener Instance, String Cmd){
    ExitButton.addActionListener (Instance);
    ExitButton.setActionCommand(Cmd);
    return ExitButton.getActionCommand();
}
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton AddButton;
    private javax.swing.JButton ExitButton;
    private javax.swing.JList FileList;
    private javax.swing.JFileChooser FileSelDialog;
    private javax.swing.JButton OpenButton;
    private javax.swing.JButton RemoveButton;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JSeparator jSeparator1;
    // End of variables declaration//GEN-END:variables

    /**
     *
     * @return
     */
    public String getDirectory() {
        //throw new UnsupportedOperationException("Not yet implemented");
        return FileSelDialog.getCurrentDirectory().getName();
    }

    /**
     * @return the result
     */
    public int getResult() {
        return result;
    }

    /**
     * @param result the result to set
     */
    public void setResult(int result) {
        this.result = result;
    }
    public String [] getSelectionArray(){
        int nFiles = FileListData.getSize();
        String [] Path = new String[nFiles];
        for(int i = 0 ; i < nFiles ; i++){
            Path[i] = (String) FileListData.get(i);
        }
        return Path;
    }
    public String[] relativise(){
        int nFiles = FileListData.getSize();
        String [] relPath = new String[nFiles];
        String [] Path = getSelectionArray();
        if(Path.length == 0){
            ij.IJ.showMessage("Error Messg", "No data files to process");
            return null;
        }
        String ref = Path[0].substring(0,Path[0].lastIndexOf(File.separator)+1);           //relativise wrt to this path
        int offset = 0;
        relPath[0] = Path[0].substring(Path[0].lastIndexOf(File.separator)+1);
        int minOffset = ref.length(), maxOffset =  ref.lastIndexOf(File.separator); // Rename the variables as
                                                    //diffLen and cmmLen

        for(int i = 1 ; i < Path.length ; i++){
            offset = findDiff(ref,Path[i].substring(0,Path[i].lastIndexOf(File.separator)+1));
            maxOffset = (offset > maxOffset )? offset : maxOffset ;
            minOffset = (offset < minOffset) ? offset : minOffset ;
            relPath[i] = Path[i].substring(offset-1);
        }
        if(maxOffset > 0)
            relPath[0] = Path[0].substring(maxOffset);
        else
            relPath[0] = ref;
        return relPath;
    }

    private int findDiff(String ref, String string) {
        int offset = 0, mlen = ( ref.length()< string.length()) ? ref.length() : string.length();

        while(offset < mlen && ref.charAt(offset)== string.charAt(offset))
            offset++;
        return offset--;
    }

}
