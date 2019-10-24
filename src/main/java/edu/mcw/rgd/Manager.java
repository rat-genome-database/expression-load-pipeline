package edu.mcw.rgd;


import edu.mcw.rgd.dao.impl.GeneExpressionDAO;
import edu.mcw.rgd.dao.impl.OntologyXDAO;
import edu.mcw.rgd.dao.impl.PhenominerDAO;
import edu.mcw.rgd.dao.impl.XdbIdDAO;
import edu.mcw.rgd.datamodel.RgdId;
import edu.mcw.rgd.datamodel.ontologyx.Term;
import edu.mcw.rgd.datamodel.pheno.Experiment;
import edu.mcw.rgd.datamodel.pheno.GeneExpressionRecord;
import edu.mcw.rgd.datamodel.pheno.GeneExpressionRecordValue;
import edu.mcw.rgd.datamodel.pheno.Sample;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.FileSystemResource;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;

public class Manager {

    private List<String> exprDesignFiles;
    private List<Sample> samples = new ArrayList<>();
    private Set<Experiment> experiments = new TreeSet<Experiment>(new Comparator() {
        @Override
        public int compare(Object o1, Object o2) {
            Experiment e1 = (Experiment)o1;
            Experiment e2 = (Experiment)o2;
            return e1.getTraitOntId().compareTo(e2.getTraitOntId());
        }
    });

    private DAO dao = new DAO();
    private Map<Integer,String> geneExpressionRecordMap = new HashMap<>();
    private Map<Integer,GeneExpressionRecord> geneExpressionRecords = new HashMap<>();
    private int noOfRuns;
    private Map<String,String> strainOntIds;
    private int studyId;
    private String expressionUnit;
    private int mapKey;
    private String tpmFile;
    private String designFile;

    private Map headerFormat;
    private List<String> headerVal;
    private Map<String,String> cmoIDs = new HashMap<>();



    Logger log = Logger.getLogger("core");

    Logger logSummary = Logger.getLogger("status");

    public static void main(String[] args) throws Exception {

        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        new XmlBeanDefinitionReader(bf).loadBeanDefinitions(new FileSystemResource("properties/AppConfigure.xml"));
        Manager manager = (Manager) (bf.getBean("manager"));

        try {
            manager.createSamplesExperimentsGeneExpressionRecord();
        } catch (Exception e) {
            manager.log.error(e);
            throw e;
        }
    }

    public void loadTPMValues() throws  Exception{


        FileReader valReader = new FileReader(getTpmFile());
        BufferedReader tpmsReader = new BufferedReader(valReader);
        List<String> animals = new ArrayList<>();
        HashMap<String,GeneExpressionRecordValue> sampleData = new HashMap<>();
        Map<Integer,HashMap> data = new HashMap<>();
        String tpmsLine = null;
        int count  = 0;

        while(( tpmsLine = tpmsReader.readLine() ) != null) {
            if (tpmsLine.startsWith("#"))
                continue;
            else if (tpmsLine.startsWith("Gene")) {
                String[] cols = tpmsLine.split("[\t]", -1);
                //Ignore first 2 cols as they are gene Id and gene name for now.
                int i = 0;
                for (String col : cols) {
                    if (i != 0 && i != 1)
                        animals.add(col);
                    i++;
                }
            } else {

                String[] cols = tpmsLine.split("[\t]", -1);
                String ensembleID = cols[0];
                int rgdId = dao.getRGDIdsByXdbId(20, ensembleID);
                if (rgdId != 0) {
                    int i = 0;
                    for (String col : cols) {
                        if (!col.isEmpty()) {
                            int j = i - 2;
                            String s = null;
                            if (i != 0 && i != 1) {
                                s = animals.get(j);
                                GeneExpressionRecordValue rec = new GeneExpressionRecordValue();
                                rec.setExpressedObjectRgdId(rgdId);

                                rec.setExpressionValue(Double.parseDouble(col));

                                rec.setExpressionUnit(getExpressionUnit());
                                rec.setExpressionMeasurementAccId(cmoIDs.get(s));
                                //rec.setGeneExpressionRecordId(geneExpressionRecords.get(s).getId());
                                rec.setMapKey(getMapKey());

                                sampleData.put(s, rec);
                            }

                            for (int id : geneExpressionRecordMap.keySet()) {
                                if (geneExpressionRecordMap.get(id).equalsIgnoreCase(s)) {
                                    GeneExpressionRecordValue value = sampleData.get(s);
                                    value.setGeneExpressionRecordId(id);
                                    int geneExprValueId = 0;
                                    geneExprValueId = dao.getGeneExprValueId(value);
                                    if (geneExprValueId == 0) {
                                        geneExprValueId = dao.insertGeneExpressionRecordValue(value);
                                        log.info("Inserted Gene Expression Record Value :" + geneExprValueId);
                                        System.out.println("Inserted Gene Expression Record Value :" + geneExprValueId);
                                    }
                                    value.setId(geneExprValueId);
                                }
                            }


                        }

                        i++;
                    }
                        data.put(rgdId, sampleData);
                    logSummary.info("Completed rgdId " + rgdId + " " + cols[1]);
                    }else{
                        count++;

                    }
            }
        }
        logSummary.info("Total Samples in Data Inserted : " + sampleData.size());
        logSummary.info("Total Genes in Data Inserted : " + data.size());
        logSummary.info("Total genes not found : " + count);
        tpmsReader.close();
    }
    public void createSamplesExperimentsGeneExpressionRecord() throws Exception{

        FileReader fileReader=new FileReader(getDesignFile());
        BufferedReader reader=new BufferedReader(fileReader);




        String line = null;
        Map<String,Integer> headerIndex = new HashMap<>();
        int experimentId = 0;
        int sampleId = 0;
        while(( line = reader.readLine() ) != null) {
            String[] cols = line.split("[\t]", -1);
            int i = 0;
            if(headerIndex.keySet().size() == i) {
                for (String col : cols) {
                    headerIndex.put(col, i);
                    i++;
                }
            } else {
                Sample sample = new Sample();
                Experiment experiment = new Experiment();
                GeneExpressionRecord geneExpressionRecord = new GeneExpressionRecord();
                String sex = null;
                String age = null;
                int ageHigh = 0;
                int ageLow = 0;
                if(headerIndex.containsKey("Sample Characteristic[sex]") && headerIndex.containsKey("Sample Characteristic[age]")) {
                    sex = cols[headerIndex.get("Sample Characteristic[sex]")];
                    age = cols[headerIndex.get("Sample Characteristic[age]")];
                    if(age.contains("week")) {
                        ageHigh = Integer.valueOf(age.split("week")[0].trim());
                        ageHigh = ageHigh*7;
                        ageLow = ageHigh;
                    }else if(age.contains("day")) {
                        if(cols[headerIndex.get("Sample Characteristic[developmental stage]")].equalsIgnoreCase("embryo")) {
                            ageLow = Integer.valueOf(age.split("day")[0].trim()) - 23;
                            ageHigh = ageLow + 2;
                        } else if(cols[headerIndex.get("Sample Characteristic[developmental stage]")].equalsIgnoreCase("postnatal")) {
                            ageHigh = Integer.valueOf(age.split("day")[0].trim());
                            ageLow = ageHigh;
                        }
                    }

                    sample.setAgeDaysFromHighBound(ageHigh);
                    sample.setAgeDaysFromLowBound(ageLow);
                    sample.setSex(sex);
                } else {
                    sample.setAgeDaysFromHighBound(null);
                    sample.setAgeDaysFromLowBound(null);
                    sample.setSex("not specified");
                }



                String strain = cols[headerIndex.get("Sample Characteristic[strain]")];
                String strainOntId = (String)(getStrainOntIds().get(strain));

                String tissue = cols[headerIndex.get("Sample Characteristic Ontology Term[organism part]")];
                String tissueOntId = tissue.split("http://purl.obolibrary.org/obo/")[1];
                tissueOntId = tissueOntId.replace("_",":");

                String part = cols[headerIndex.get("Sample Characteristic[organism part]")];
                String exprName = part + " molecular composition trait";
                if(part.equalsIgnoreCase("forebrain") || part.equalsIgnoreCase("hindbrain"))
                    exprName = "brain molecular composition trait";
                else if(part.equalsIgnoreCase("skeletal muscle tissue")){
                    exprName = "muscle molecular composition trait";
                }

                String traitId = null;
                traitId = dao.getTermByTermName(exprName,"VT");
                if(traitId == null)
                {   exprName = part + " morphology trait";
                    traitId = dao.getTermByTermName(exprName,"VT");
                }


                experiment.setStudyId(getStudyId());
                experiment.setName(exprName);
                experiment.setTraitOntId(traitId);
                experiments.add(experiment);


                    experimentId = dao.getExperimentId(experiment);
                    if(experimentId == 0) {
                        experimentId = dao.insertExperiment(experiment);
                        System.out.println("Inserted experiment :" + experimentId);
                    }
                    log.info("Inserted experiment :" + experimentId);


                experiment.setId(experimentId);




                sample.setNumberOfAnimals(getNoOfRuns());
                sample.setStrainAccId(strainOntId);
                sample.setTissueAccId(tissueOntId);
                samples.add(sample);


                    sampleId = dao.getSampleId(sample);
                    if(sampleId == 0) {
                        sampleId = dao.insertSample(sample);
                        System.out.println("Inserted Sample :" + sampleId);
                    }
                    log.info("Inserted Sample :" + sampleId);


                sample.setId(sampleId);
                String header = "";
                for(int k = 0; k < getHeaderVal().size(); k++){
                    header += cols[headerIndex.get(getHeaderVal().get(k))];
                    if(k != getHeaderVal().size()-1)
                        header = header + getHeaderFormat().get("delimiter") + " ";
                }


                    geneExpressionRecord.setExperimentId(experiment.getId());
                    geneExpressionRecord.setSampleId(sample.getId());
                    geneExpressionRecord.setLastModifiedBy("hsnalabolu");
                    geneExpressionRecord.setCurationStatus(20);
                    geneExpressionRecord.setSpeciesTypeKey(3);

                    int geneExprRecId = dao.getGeneExprRecordId(geneExpressionRecord);
                    if(geneExprRecId == 0) {
                        geneExprRecId = dao.insertGeneExpressionRecord(geneExpressionRecord);
                        System.out.println("Inserted geneExpressionRecord :" + geneExprRecId);
                    }
                    geneExpressionRecord.setId(geneExprRecId);
                    log.info("Inserted geneExpressionRecord :" + geneExprRecId);


                geneExpressionRecordMap.put(geneExprRecId,header);
                geneExpressionRecords.put(geneExprRecId,geneExpressionRecord);
                String cmoId = "";

                String term = part + " ribonucleic acid composition measurement";
                if(part.equalsIgnoreCase("forebrain") || part.equalsIgnoreCase("hindbrain"))
                    cmoId = dao.getTermByTermName("brain ribonucleic acid composition measurement","CMO");
                else if (part.equalsIgnoreCase("skeletal muscle tissue"))
                    cmoId = dao.getTermByTermName("skeletal muscle ribonucleic acid composition measurement","CMO");
                else
                    cmoId = dao.getTermByTermName(term,"CMO");

                cmoIDs.put(header,cmoId);
            }

        }

        logSummary.info("Experiments Inserted : " + experiments.size());
        logSummary.info("Samples Inserted : " + samples.size());
        logSummary.info("Gene Expression Records Inserted : " + geneExpressionRecords.size());
        reader.close();
        loadTPMValues();
    }

    public Map getStrainOntIds() {
        return strainOntIds;
    }

    public void setStrainOntIds(Map strainOntIds) {
        this.strainOntIds = strainOntIds;
    }

    public int getStudyId() {
        return studyId;
    }

    public void setStudyId(int studyId) {
        this.studyId = studyId;
    }

    public String getExpressionUnit() {
        return expressionUnit;
    }

    public void setExpressionUnit(String expressionUnit) {
        this.expressionUnit = expressionUnit;
    }

    public int getNoOfRuns() {
        return noOfRuns;
    }

    public void setNoOfRuns(int noOfRuns) {
        this.noOfRuns = noOfRuns;
    }

    public Map getHeaderFormat() {
        return headerFormat;
    }

    public void setHeaderFormat(Map headerFormat) {
        this.headerFormat = headerFormat;
    }

    public List getHeaderVal() {
        return headerVal;
    }

    public void setHeaderVal(List headerVal) {
        this.headerVal = headerVal;
    }

    public int getMapKey() {
        return mapKey;
    }

    public void setMapKey(int mapKey) {
        this.mapKey = mapKey;
    }

    public String getDesignFile() {
        return designFile;
    }

    public void setDesignFile(String designFile) {
        this.designFile = designFile;
    }

    public String getTpmFile() {
        return tpmFile;
    }

    public void setTpmFile(String tpmFile) {
        this.tpmFile = tpmFile;
    }
}

