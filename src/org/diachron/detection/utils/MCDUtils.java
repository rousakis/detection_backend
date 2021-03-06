/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.diachron.detection.utils;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.diachron.detection.associations.AssocManager;
import org.diachron.detection.complex_change.CCManager;
import org.diachron.detection.exploit.ChangesExploiter;
import org.diachron.detection.repositories.JDBCVirtuosoRep;
import org.openrdf.repository.RepositoryException;

/**
 *
 * @author rousakis
 */
public class MCDUtils {

    private Properties propFile;
    private String changesOntologySchema;
    private String datasetURI;
    private List<String> changesOntologies;
    private ChangesDetector detector;
    private boolean detectAssoc;
    private final String datasetsGraph = "http://datasets";

    public MCDUtils(Properties prop, String datasetUri, boolean assoc) throws IOException, ClassNotFoundException, SQLException, RepositoryException {
        propFile = prop;
        this.datasetURI = datasetUri;
        String tmpUri = datasetUri;
        if (datasetUri.endsWith("/")) {
            tmpUri = datasetUri.substring(0, datasetUri.length() - 1);
        }
        this.changesOntologySchema = tmpUri + "/changes/schema";
        this.detector = new ChangesDetector(prop, null, changesOntologySchema, null);  //the changes ontology is null initially
        setChangesOntologies();
        detectAssoc = assoc;
    }

    public void detectDatasets(boolean complexOnly) throws Exception {
        DatasetsManager dManager = new DatasetsManager(getJDBCRepository(), datasetURI);
        List<String> versions = new ArrayList(dManager.fetchDatasetVersions().keySet());
        AssocManager assoc = new AssocManager(dManager.getJDBCVirtuosoRep(), datasetURI, true);
        for (int i = 1; i < versions.size(); i++) {
            String v1 = versions.get(i - 1);
            String v2 = versions.get(i);
            ChangesManager cManager = new ChangesManager(getJDBCRepository(), datasetURI, v1, v2, false);
            String changesOntology = cManager.getChangesOntology();
            detector.setChangesOntology(changesOntology);
            detector.getJdbc().clearGraph(detector.getChangesOntology(), false);
            if (detectAssoc) {
                detector.setAssociationsGraph(assoc.getAssocGraph(v1, v2));
            } else {
                detector.setAssociationsGraph(null);
            }
            if (!complexOnly) {
                detector.detectAssociations(v1, v2);
                detector.detectSimpleChanges(v1, v2, null);
            }
            detector.detectComplexChanges(v1, v2, null);
            System.out.println("-----");
        }
    }

    public void deleteCCWithLessPriority(String changeName) throws Exception {
        ChangesExploiter exploiter = new ChangesExploiter(getJDBCRepository(), datasetURI, true);
        List<String> versions = exploiter.getChangesOntologies();
        for (String version : versions) {
            CCManager manager = new CCManager(propFile, exploiter.getChangesOntologySchema());
            try {
                manager.deleteComplexChangeInstWithLessPr(version, changeName);
            } finally {
                manager.terminate();
            }
        }
    }

    public boolean deleteMultipleCC(List<String> names) {
        CCManager ccDef = null;
        boolean result = false, retVal = false;
        try {
            ccDef = new CCManager(propFile, changesOntologySchema);
            for (String changesOntology : changesOntologies) {
                retVal = ccDef.deleteComplexChanges(changesOntology, names, true);
                if (retVal) {
                    result = retVal;
                }
            }
            retVal = ccDef.deleteComplexChanges(changesOntologySchema, names, false);
            if (retVal) {
                result = retVal;
            }
            if (result) {
                detectDatasets(true);
            }
        } catch (Exception ex) {
            System.out.println("Exception: " + ex.getMessage() + " occured .");
        } finally {
            if (ccDef != null) {
                ccDef.terminate();
            }
        }
        return result;
    }

    public boolean deleteCC(String name) throws Exception {
        boolean result = false, retVal = false;
        CCManager ccDef = null;
        try {
            ccDef = new CCManager(propFile, changesOntologySchema);
            for (String changesOntology : changesOntologies) {
                retVal = ccDef.deleteComplexChange(changesOntology, name, true);
                if (retVal) {
                    result = retVal;
                }
            }
            retVal = ccDef.deleteComplexChange(changesOntologySchema, name, false);
            if (retVal) {
                result = retVal;
            }
            if (result) {
                detectDatasets(true);
            }
        }finally {
            if (ccDef != null) {
                ccDef.terminate();
            }
        }
        return result;
    }

    private void setChangesOntologies() throws SQLException {
        StringBuilder datasetChanges = new StringBuilder();
        if (datasetURI.endsWith("/")) {
            datasetChanges.append(datasetURI.substring(0, datasetURI.length() - 1));
        } else {
            datasetChanges.append(datasetURI);
        }
        datasetChanges.append("/changes");
        this.changesOntologies = new ArrayList<>();
        String query = "select ?ontol from <" + datasetsGraph + "> where {\n"
                + "<" + datasetChanges + "> rdfs:member ?ontol.\n"
                + "?ontol co:old_version ?v1.\n"
                + "}  order by ?v1";
        JDBCVirtuosoRep jdbc = getJDBCRepository();
        ResultSet results = jdbc.executeSparqlQuery(query, false);
        try {
            if (results.next()) {
                do {
                    this.changesOntologies.add(results.getString(1));
                } while (results.next());
            }
        } finally {
            if(results != null){
                results.close();
            }
        }
    }

    public JDBCVirtuosoRep getJDBCRepository() {
        return detector.getJdbc();
    }

    public void terminate() {
        this.detector.terminate();
    }

}
