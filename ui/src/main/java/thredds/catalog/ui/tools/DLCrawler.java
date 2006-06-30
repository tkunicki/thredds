// $Id: DLCrawler.java,v 1.2 2006/06/06 16:17:07 caron Exp $
/*
 * Copyright 1997-2004 Unidata Program Center/University Corporation for
 * Atmospheric Research, P.O. Box 3000, Boulder, CO 80307,
 * support@unidata.ucar.edu.
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or (at
 * your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package thredds.catalog.ui.tools;

import ucar.util.prefs.PreferencesExt;
import ucar.util.prefs.ui.*;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.ListSelectionEvent;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.net.URI;
import java.net.URL;
import java.net.MalformedURLException;
import java.io.IOException;
import java.io.InputStream;

import thredds.catalog.ui.CatalogChooser;
import thredds.catalog.ui.CatalogTreeView;
import thredds.catalog.*;
import thredds.catalog.dl.DIFWriter;
import thredds.catalog.dl.ADNWriter;
import thredds.ui.*;


/**
 * Experimental widget for extracting and modifying catalogs. Do not use yet.
 *
 * @author John Caron
 * @version $Id: DLCrawler.java,v 1.2 2006/06/06 16:17:07 caron Exp $
 */

public class DLCrawler extends JPanel {
  static private final String SOURCE_WINDOW_SIZE = "SourceWindowSize";
  static private final String STATUS_WINDOW_SIZE = "StatusWindowSize";

  private PreferencesExt prefs;
  private Component myParent;

  // ui
  private CatalogChooser catalogChooser;
  private CatalogTreeView tree;
  private BeanTableSorted dsTable, daTable;
  private JTabbedPane tabbedPane;
  private JSplitPane splitV;
  private ArrayList tables = new ArrayList();
  private IndependentWindow sourceWindow = null, statusWindow = null;
  private TextGetPutPane sourcePane;
  private TextHistoryPane statusPane;

  // data
  private ArrayList daList = new ArrayList();
  private ArrayList dsList = new ArrayList();
  //private InvCatalogImpl currentCatalog = null;
  //private DatasetBean currentBean = null;

  private DIFWriter difWriter = new DIFWriter();
  private ADNWriter adnWriter = new ADNWriter();

  private boolean debugEvents = false, debugBeans = false;

  public DLCrawler(PreferencesExt prefs, Component parent) {
    this.prefs = prefs;
    this.myParent = parent;

    // create the catalog chooser
    PreferencesExt node = (prefs == null) ? null : (PreferencesExt) prefs.node("catChooser");
    catalogChooser = new CatalogChooser(node, true, false, true);
    //catalogChooser.setCatrefEvents( true);
    catalogChooser.addPropertyChangeListener(  new java.beans.PropertyChangeListener() {
      public void propertyChange(java.beans.PropertyChangeEvent e) {
        if (debugEvents)
          System.out.println("CatalogEnhancer chooser propertyChange= " +e.getPropertyName());
        if (e.getPropertyName().equals("Catalog")) {
          InvCatalog cat = catalogChooser.getCurrentCatalog();
          String orgURLs = cat.findProperty("CatalogGenConfigOrigURL");
          if (orgURLs != null) {
            try {
              URI orgURL = cat.resolveUri(orgURLs);
              orgURLs = orgURL.toString();
              if (orgURLs.equals( cat.getUriString())) return;

              int val = JOptionPane.showConfirmDialog(myParent,
                "This catalog was created by the Catalog Generator program\n" +
                 "If you want to change it you should change the catgen config file\n" +
                 "Do you want to edit the catgen config file instead?",
                 "WARNING - generated file; will be overrwritten",
                 JOptionPane.YES_NO_OPTION);
              if (val == JOptionPane.YES_OPTION) {
                catalogChooser.setCatalog( orgURLs);
              }

            } catch (java.net.URISyntaxException se) {
              return;
            }
          }
        }
      }
    });

    // catch tree events to synch with dataset table
    tree = catalogChooser.getTreeView();
    tree.setOpenDatasetScans( false);
    tree.addPropertyChangeListener(  new java.beans.PropertyChangeListener() {
      public void propertyChange( java.beans.PropertyChangeEvent e) {
        if (debugEvents) System.out.println("CatalogEnhancer tree propertyChange= "+e.getPropertyName());
         // see if a new catalog is set
        if (e.getPropertyName().equals("Catalog")) {
          daList = new ArrayList();
          dsList = new ArrayList();
          dsTable.setBeans( dsList);
          daTable.setBeans( daList);

        } else if (e.getPropertyName().equals("TreeNode")) {
          InvDatasetImpl ds = (InvDatasetImpl) e.getNewValue();
          addDataset(ds);
          addDatasetAccess(ds);

        } else if (e.getPropertyName().equals("Selection")) {
          InvDatasetImpl ds = (InvDatasetImpl) e.getNewValue();
          DatasetBean dsBean = findDatasetBean( ds);
          dsTable.setSelectedBean( dsBean);
        }
      }
    });

    // dataset bean table
    dsTable = new BeanTableSorted(DatasetBean.class, (PreferencesExt) prefs.node("dsBeans"), false);
    dsTable.addListSelectionListener( new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        DatasetBean bean = (DatasetBean) dsTable.getSelectedBean();
        InvDatasetImpl selectedDataset = (InvDatasetImpl) bean.dataset();
        catalogChooser.setSelectedDataset( selectedDataset);
      }
    });

    // access bean table
    daTable = new BeanTableSorted(AccessBean.class, (PreferencesExt) prefs.node("dsBeans"), false);

    /// put tables in tabbed pane
    tabbedPane = new JTabbedPane(JTabbedPane.TOP);
    tabbedPane.addTab("Datasets", dsTable);
    tabbedPane.addTab("Access", daTable);

    // layout
    splitV = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, catalogChooser, tabbedPane);
    splitV.setDividerLocation(prefs.getInt("splitPos", 500));

    setLayout( new BorderLayout());
    add( splitV, BorderLayout.CENTER);

    JPanel buttPanel = new JPanel();
    add( buttPanel, BorderLayout.SOUTH);

     // window to show DL status
    statusPane = new TextHistoryPane( false);
    statusWindow = new IndependentWindow( "Digital Library Status", BAMutil.getImage( "thredds"), statusPane);
    statusWindow.setBounds((Rectangle)prefs.getBean(STATUS_WINDOW_SIZE, new Rectangle(50, 50, 725, 450)));

    JButton status = new JButton("Status DL");
    buttPanel.add(status);
    status.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent evt) {
        DatasetBean b = (DatasetBean) dsTable.getSelectedBean();
        statusPane.setText( b.status());
        statusWindow.show();
      }
    });

     // window to show source
    sourcePane = new TextGetPutPane( (PreferencesExt) prefs.node("getputPane"));
    sourceWindow = new IndependentWindow( "Source", BAMutil.getImage( "thredds"), sourcePane);
    sourceWindow.setBounds((Rectangle)prefs.getBean(SOURCE_WINDOW_SIZE, new Rectangle(50, 50, 725, 450)));

    // add a show source button to catalog chooser
    JButton catSource = new JButton("Source");
    catSource.addActionListener( new ActionListener() {
      public void actionPerformed(ActionEvent evt) {
        InvCatalogImpl cat = (InvCatalogImpl) catalogChooser.getCurrentCatalog();
        if (cat == null) return;
        String catURL = catalogChooser.getCurrentURL();
        try {
          sourcePane.setCatalog(cat.getBaseURI().toString(), cat);
        } catch (IOException ioe) {
          ioe.printStackTrace();
        }

        sourcePane.gotoTop();
        sourceWindow.show();
      }
    });
    buttPanel.add(catSource);

    JButton openAllButton = new JButton("Open All");
    buttPanel.add(openAllButton);
    openAllButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent evt) {
        tree.openAll( false);
      }
    });

    JButton harvestButton = new JButton("Harvest");
    buttPanel.add(harvestButton);
    harvestButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent evt) {
        harvest();
      }
    });
  }

  private void harvest() {
     StringBuffer mess = new  StringBuffer();
     for (int i = 0; i < dsList.size(); i++) {
       DatasetBean bean =  (DatasetBean) dsList.get(i);
       InvDataset ds = bean.dataset();
       if (ds.isHarvest())
         difWriter.doOneDataset( ds, "C:/temp/dif2/", mess);
     }
  }

  /* private void setCatalog(InvCatalogImpl catalog) {
    this.currentCatalog = catalog;
    dsTable.setBeans( catBean.getDatasetBeans());
  } */

  /* this transforms the catalog to a local one, modifying the catalogRefs
  private void writeCatalog(InvCatalogImpl catalog, String filename, InvCatalogFactory catFactory) {
    collapseCatalogRefs( catalog.getDataset());

    try {
      catFactory.writeXML( catalog, filename);
    } catch (IOException e) {
      JOptionPane.showMessageDialog(myParent, "Catalog failed to write to file= " + filename+
          "\n"+e.getMessage());
    }
    JOptionPane.showMessageDialog( myParent, "Catalog written to file= " + filename );
  }

  private void collapseCatalogRefs(InvDataset ds) {

    ArrayList dlist = (ArrayList) ds.getDatasets();
    for (int i=0; i<dlist.size(); i++) {
      InvDataset dds = (InvDataset) dlist.get(i);

      if (dds instanceof InvCatalogRef) {
        InvCatalogRef catRef = (InvCatalogRef) dds;
        if (!catRef.isRead()) continue;

        // collapse: should change names here
        dlist.remove(dds);
        dlist.add(i, catRef.getProxyDataset());
        System.out.println(" collapsed "+catRef.getName());
      }

      collapseCatalogRefs( dds);
    }
  } */

  public void save() {
    prefs.putInt("splitPos", splitV.getDividerLocation());
    catalogChooser.save();
    dsTable.saveState(false);
    daTable.saveState(false);
  }

  //////////////////////////////////////////////////////////////////////////////
  // check URLs
  private boolean debugCheckUrl = false;

  private class CheckURLsTask extends ProgressMonitorTask {
    int taskLength = 0;
    int count = 0;

    public void run() {
      Iterator iter = getDatasetAccessBeans().iterator();
      while (iter.hasNext()) {
        AccessBean bean = (AccessBean) iter.next();
        String urlOK = bean.getUrlOk();
        if (urlOK.length() > 0) continue; // already been checked
        if (cancel) break;

        InvAccess access = bean.access();
        if (debugCheckUrl) System.out.print("Try to open "+access.getStandardUrlName());
        String status = checkURL( makeURL(access));
        if (debugCheckUrl) System.out.println(" "+status);
        count++;

        bean.setUrlOk( status);
      }
     success = !cancel && !isError();
     done = true;    // do last!
    }

    public String getNote() { return count +" URLs out of "+taskLength; }
    public int getProgress() { return count; }

    public int getTaskLength() {
      taskLength = 0;
      Iterator iter = getDatasetAccessBeans().iterator();
      while (iter.hasNext()) {
        AccessBean bean = (AccessBean) iter.next();
        if (bean.getUrlOk().length() > 0) continue; // already been checked
        taskLength += bean.dataset().getAccess().size();
      }
      return taskLength;
    }
  }

  private String checkURL( String urlString) {
    URL url = null;
    try {
      url = new URL( urlString);
    } catch ( MalformedURLException e) {
      System.out.println(" BAD url "+urlString+" = "+e.getMessage());
      return "BAD";
    }

    try {
      InputStream is = url.openStream();
      is.close();
    } catch (IOException ioe) {
      System.out.println(" BAD connection "+urlString+" = "+ioe.getMessage());
      return "MISS";
    }
    return "OK";
  }

  private String makeURL( InvAccess access) {
    String urlString = access.getStandardUrlName();
    if (access.getService().getServiceType() == ServiceType.DODS)
      urlString = urlString + ".dds";
    return urlString;
  }

    public void clear() {
      daList = new ArrayList();
      dsList = new ArrayList();
    }

    /** list of CatalogBean.DatasetAccessBean */
    public ArrayList getDatasetAccessBeans() { return daList; }

    /** list of CatalogBean.DatasetBean */
    public ArrayList getDatasetBeans() { return dsList; }

    private void addDatasets(InvDatasetImpl ds) {
      addDataset( ds);

      // skip unread catalogRef
      if (ds instanceof InvCatalogRef) {
        InvCatalogRef catRef = (InvCatalogRef) ds;
        if (!catRef.isRead()) return;
      }

      if (ds.hasAccess()) {
        Iterator iter = ds.getAccess().iterator();
        while (iter.hasNext())
          daList.add( new AccessBean( (InvAccess) iter.next()));
      }

      // recurse
      java.util.List dlist = ds.getDatasets();
      for (int i=0; i<dlist.size(); i++) {
        InvDatasetImpl dds = (InvDatasetImpl) dlist.get(i);
        addDatasets( dds);
      }
    }

    public void addDataset(InvDatasetImpl ds) {
      DatasetBean bean = new DatasetBean( ds);
      dsTable.addBean( bean);
    }

    public void addDatasetAccess(InvDatasetImpl ds) {
      Iterator iter = ds.getAccess().iterator();
      while (iter.hasNext()) {
        AccessBean beana = new AccessBean( (InvAccess) iter.next());
        daTable.addBean( beana);
      }
    }

    public DatasetBean findDatasetBean( InvDataset ds) {
      Iterator iter = dsList.iterator();
      while (iter.hasNext()) {
        DatasetBean item = (DatasetBean) iter.next();
        if (item.dataset() == ds) return item;
      }
      return null;
    }
    public AccessBean findAccessBean( InvDataset ds) {
      Iterator iter = daList.iterator();
      while (iter.hasNext()) {
        AccessBean item = (AccessBean) iter.next();
        if (item.dataset() == ds) return item;
      }
      return null;
    }


    public AccessBean findOrAddBean( InvAccess access) {
      Iterator iter = daList.iterator();
      while (iter.hasNext()) {
        AccessBean item = (AccessBean) iter.next();
        if (item.access == access) return item;
      }
      AccessBean newBean = new AccessBean( access);
      daList.add( newBean);

      return newBean;
    }

    public class DatasetBean {
      private InvDatasetImpl ds;
      private boolean adn, dif, summary, rights, datasetScan;
      private String difMessages;

      // no-arg constructor
      public DatasetBean() {}

      // create from a dataset
      public DatasetBean( InvDatasetImpl ds) {
        this.ds = ds;
        synch();
     }

      public void synch() {
        String s = ds.getDocumentation("summary");
        summary = (s != null);
        s = ds.getDocumentation("rights");
        rights = (s != null);

        datasetScan = ds.findProperty("DatasetScan") != null;

        StringBuffer sbuff = new StringBuffer();
        sbuff.append("DIF:\n");
        dif = difWriter.isDatasetUseable( ds, sbuff);
        sbuff.append("\nADN:\n");
        adn = adnWriter.isDatasetUseable( ds, sbuff);
        difMessages = sbuff.toString();
      }

      public InvDatasetImpl dataset() { return ds; }
      public String status() { return difMessages; }

      //public String getDataType() { return ds.getDataType() == null ? "" : ds.getDataType().toString(); }
      //public String getCollection() { return ds.getCollectionType() == null ? "" : ds.getCollectionType().toString(); }
      public String getId() { return ds.getID() == null ? "" : ds.getID(); }
      public String getName() { return ds.getName(); }

      public boolean isAdn() { return adn; }
      public boolean isDif() { return dif; }
      public boolean isDatasetScan() { return datasetScan; }
      public boolean isHarvest() { return ds.isHarvest(); }
      public boolean isGeo() {
        ThreddsMetadata.GeospatialCoverage geo = ds.getGeospatialCoverage();
        return (geo != null) && geo.isValid();
      }
      public boolean isTime() { return ds.getTimeCoverage() != null; }
      public boolean isVars() { return ds.getVariables().size() > 0; }
      public boolean isPublish() { return (ds.getPublishers().size() > 0); }
      public boolean isRights() { return rights; }
      public boolean isSummary() { return summary; }
    }

    public class AccessBean {
      // static public String editableProperties() { return "title include logging freq"; }

      private thredds.catalog.InvAccess access;
      private String name, url, URLok = "";
      private thredds.catalog.DataType dataType;
      private DataFormatType dataFormatType;
      private ServiceType serviceType;
      private int ngrids, readTime;
      private boolean hasBoundingBox, hasTimeRange, hasStandardQuantities;

      // no-arg constructor
      public AccessBean() {}

      // create from an access
      public AccessBean( InvAccess access) {
        this.access = access;
        InvDataset ds = access.getDataset();
        setName( ds.getName());
        setServiceType( access.getService().getServiceType());
        setUrl( access.getStandardUrlName());

        dataFormatType = access.getDataFormatType();

        // if (debug) System.out.println(" DatasetAccessBean added= "+getUrl());

        /* String n = ds.findProperty("ngrids");
        if (n != null)
          setNgrids( Integer.parseInt(n));

        String t = ds.findProperty("readTime");
        if (t != null)
          setReadTime( Integer.parseInt(t));

        setBB(null != ds.findProperty("BoundingBox"));
        setTR(null != ds.findProperty("DateMin") || null != ds.findProperty("DateMax"));
        setSQ(null != ds.findProperty("StandardQuantities")); */
      }

      public InvDataset dataset() { return access.getDataset(); }
      public InvAccess access() { return access; }

      public String getName() { return name; }
      public void setName(String name) { this.name = name; }

      public String getServiceType() { return serviceType.toString(); }
      public void setServiceType(ServiceType serviceType) { this.serviceType = serviceType; }

      public String getFormat() { return (dataFormatType == null) ? "" : dataFormatType.toString(); }

      public String getUrl() { return url; }
      public void setUrl(String url) { this.url = url; }

      /** Get URLok */
      public String getUrlOk() { return URLok; }
      /** Set URLok */
      public void setUrlOk( String URLok) { this.URLok = URLok; }
    }

}