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
import edu.mcw.rgd.process.mapping.MapManager;
import org.apache.log4j.Logger;
import org.apache.log4j.pattern.IntegerPatternConverter;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.FileSystemResource;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;

public class Manager {



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
    private Map<String,GeneExpressionRecord> geneExpressionRecords = new HashMap<>();

    private int studyId;
    private String expressionUnit;
    private int mapKey;
    private String tpmFile;
    private String phenoFile;
    private String sampleFile;
    private boolean firstRun = true;

    private List<GeneExpressionRecordValue> loaded = new ArrayList<>();
    private List<Integer> loadedRgdIds = new ArrayList<>();
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
        List<String> samples = new ArrayList<>();
        String tpmsLine = null;
        //loadedRgdIds = dao.getExistingIds(studyId);

        while(( tpmsLine = tpmsReader.readLine() ) != null) {
            if (tpmsLine.startsWith("5") || tpmsLine.startsWith("#")) {
                continue;
            }else if (tpmsLine.startsWith("Name")) {
                String[] cols = tpmsLine.split("[\t]", -1);
                //Ignore first 2 cols as they are gene Id and gene name for now.
                int i = 0;
                for (String col : cols) {
                    if (i != 0 && i != 1)
                        samples.add(col);
                    i++;
                }
            } else {

                String[] cols = tpmsLine.split("[\t]", -1);
                String ensembleID;

                if(cols[0].contains(".")) {
                    String[] ids = cols[0].split("\\.");
                    ensembleID = ids[0];
                }else ensembleID = cols[0];
                int rgdId = dao.getRGDIdsByXdbId(20, ensembleID);
                if (rgdId != 0 ) {
                    int i = 0;
                    for (String col : cols) {
                        if (!col.isEmpty()) {
                            int j = i - 2;
                            String s = null;
                            if (i != 0 && i != 1) {
                                s = samples.get(j);
                                GeneExpressionRecordValue rec = new GeneExpressionRecordValue();
                                rec.setExpressedObjectRgdId(rgdId);

                                rec.setExpressionValue(Double.parseDouble(col));

                                rec.setExpressionUnit(getExpressionUnit());
                                rec.setExpressionMeasurementAccId(cmoIDs.get(s));

                                rec.setGeneExpressionRecordId(geneExpressionRecords.get(s).getId());
                                rec.setMapKey(getMapKey());
                                if ( rec.getExpressionValue() != 0) {
                                    loaded.add(rec);
                                }

                            }

                        }

                        i++;
                    }

                   dao.insertGeneExpressionRecordValues(loaded);
                    loaded.clear();
                    logSummary.info("Completed rgdId " + rgdId + " " + cols[1]);
                    System.out.println("Completed rgdId " + rgdId + " " + cols[1]);
                    }
            }
        }
        tpmsReader.close();
    }


    public void createSamplesExperimentsGeneExpressionRecord() throws Exception{


            FileReader fileReader = new FileReader(getPhenoFile());
            BufferedReader reader = new BufferedReader(fileReader);
            FileReader sampleReader = new FileReader(getSampleFile());
            BufferedReader reader1 = new BufferedReader(sampleReader);
            FileReader valReader = new FileReader(getTpmFile());
            BufferedReader tpmsReader = new BufferedReader(valReader);

            List<String> samples = new ArrayList<>();
            Map<String, Sample> phenoData = new HashMap<>();

            String line;
            int i = 1;
            while ((line = tpmsReader.readLine()) != null) {
                String[] cols = line.split("[\t]", -1);
                if (i == 3) {
                    samples = Arrays.asList(cols);
                }

                i++;
            }
            tpmsReader.close();

            System.out.println("Adding phenoData");
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("SUBJID"))
                    continue;
                String[] cols = line.split("[\t]", -1);
                Sample s = new Sample();
                int sex = Integer.parseInt(cols[1]);
                if (sex == 1)
                    s.setSex("male");
                else s.setSex("female");
                String ageLow = cols[2].substring(0, 2);
                String ageHigh = cols[2].substring(3);
                s.setAgeDaysFromLowBound(Integer.parseInt(ageLow) * 365);
                s.setAgeDaysFromHighBound(Integer.parseInt(ageHigh) * 365);
                phenoData.put(cols[0], s);
            }
            reader.close();
            while ((line = reader1.readLine()) != null) {
                if (line.startsWith("SAMPID"))
                    continue;
                String[] cols = line.split("[\t]", -1);
                if (samples.contains(cols[0])) {
                    String subId = cols[0].substring(0, 10);
                    if (subId.endsWith("-"))
                        subId = subId.substring(0, subId.length() - 1);
                    System.out.println(subId);
                    Sample sample = phenoData.get(subId);
                    Experiment experiment = new Experiment();
                    GeneExpressionRecord geneExpressionRecord = new GeneExpressionRecord();
                    sample.setBioSampleId(cols[0]);
                    sample.setTissueAccId("UBERON:" + cols[7]);
                    sample.setNotes(cols[3]);

                    Sample s = dao.getSampleFromBioSampleId(sample.getBioSampleId());
                    int sampleId;
                    if (s == null) {
                        sampleId = dao.insertSample(sample);
                        System.out.println("Inserted Sample :" + sampleId);
                    } else {
                        sampleId = s.getId();
                    }
                    sample.setId(sampleId);

                    String part = cols[6].toLowerCase();

                    experiment.setStudyId(getStudyId());
                    experiment.setName(getExperimentName(part));
                    experiment.setTraitOntId(getTraitId(part, getExperimentName(part)));
                    int experimentId = dao.getExperimentId(experiment);
                    if (experimentId == 0) {
                        experimentId = dao.insertExperiment(experiment);
                        System.out.println("Inserted experiment :" + experimentId);
                    }
                    experiment.setId(experimentId);

                    log.info("Inserted experiment :" + experimentId);

                    geneExpressionRecord.setExperimentId(experiment.getId());
                    geneExpressionRecord.setSampleId(sample.getId());
                    geneExpressionRecord.setLastModifiedBy("hsnalabolu");
                    geneExpressionRecord.setCurationStatus(20);
                    geneExpressionRecord.setSpeciesTypeKey(MapManager.getInstance().getMap(mapKey).getSpeciesTypeKey());

                    int geneExprRecId = dao.getGeneExprRecordId(geneExpressionRecord);
                    if (geneExprRecId == 0) {
                        geneExprRecId = dao.insertGeneExpressionRecord(geneExpressionRecord);
                        System.out.println("Inserted geneExpressionRecord :" + geneExprRecId);
                    }
                    geneExpressionRecord.setId(geneExprRecId);
                    log.info("Inserted geneExpressionRecord :" + geneExprRecId);

                    geneExpressionRecords.put(cols[0], geneExpressionRecord);
                    cmoIDs.put(cols[0], getCMOId(part));
                }
            }

            logSummary.info("Experiments Inserted : " + experiments.size());
            logSummary.info("Samples Inserted : " + samples.size());
            logSummary.info("Gene Expression Records Inserted : " + geneExpressionRecords.size());
            reader1.close();

        loadTPMValues();
      dao.updateExpressionLevel();
    }



    public String getExperimentName(String part) throws Exception{

        System.out.println(part);
        String exprName = part + " molecular composition trait";
        if(part.contains("adipose"))
            exprName = "adipose molecular composition trait";
        else if(part.contains("artery"))
            exprName = "artery molecular composition trait";
        else if(part.contains("bladder"))
            exprName = "urinary bladder morphology trait";
        else if(part.contains("brain") || part.equalsIgnoreCase("pituitary"))
            exprName = "brain molecular composition trait";
        else if(part.equalsIgnoreCase("breast - mammary tissue"))
            exprName = "mammary gland morphology trait";
        else if(part.equalsIgnoreCase("cells - cultured fibroblasts"))
            exprName = "connective tissue morphology trait";
        else if(part.equalsIgnoreCase("cells - ebv-transformed lymphocytes"))
            exprName = "lymphocyte morphology trait";
        else if(part.contains("cervix") || part.equalsIgnoreCase("vagina"))
            exprName = "female reproductive system morphology trait";
        else if(part.contains("colon"))
            exprName = "colon morphology trait";
        else if(part.contains("esophagus"))
            exprName = "gastrointestinal system morphology trait";
        else if(part.contains("fallopian tube"))
            exprName = "oviduct morphology trait";
        else if(part.contains("heart"))
            exprName = "heart molecular composition trait";
        else if(part.contains("kidney"))
            exprName = "kidney molecular composition trait";
        else if(part.equalsIgnoreCase("muscle - skeletal"))
            exprName = "skeletal muscle morphology trait";
        else if(part.equalsIgnoreCase("nerve - tibial"))
            exprName = "nervous system morphology trait";
        else if(part.contains("skin"))
            exprName = "skin molecular composition trait";
        else if(part.contains("intestine"))
            exprName = "intestine morphology trait";
        else if(part.contains("thyroid"))
            exprName = "thyroid gland morphology trait";
        else if(part.contains("blood"))
            exprName = "blood molecular composition trait";

        String traitId = dao.getTermByTermName(exprName,"VT");
        if(traitId == null)
             exprName = part + " morphology trait";


            return exprName;
    }
    public String getTraitId(String part, String exprName) throws Exception{

        String traitId = null;

        traitId = dao.getTermByTermName(exprName,"VT");
        if(traitId == null)
        {   exprName = part + " morphology trait";
            traitId = dao.getTermByTermName(exprName,"VT");
        }
        return traitId;
    }
    public String getCMOId(String part) throws Exception{

        String cmoId = "";
        String term = part + " ribonucleic acid composition measurement";

        if(part.equalsIgnoreCase("adipose - subcutaneous"))
            cmoId = dao.getTermByTermName("subcutaneous adipose tissue ribonucleic acid composition measurement","CMO");
        else if(part.equalsIgnoreCase("adipose - visceral (omentum)"))
            cmoId = dao.getTermByTermName("omental adipose tissue ribonucleic acid composition measurement","CMO");
        else if(part.equalsIgnoreCase("artery - aorta"))
            cmoId = dao.getTermByTermName("aorta ribonucleic acid composition measurement","CMO");
        else if(part.equalsIgnoreCase("artery - coronary"))
            cmoId = dao.getTermByTermName("coronary artery ribonucleic acid composition measurement","CMO");
        else if(part.equalsIgnoreCase("artery - tibial"))
            cmoId = dao.getTermByTermName("tibial artery ribonucleic acid composition measurement","CMO");
        else if(part.equalsIgnoreCase("bladder"))
            cmoId = dao.getTermByTermName("urinary bladder ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("brain - amygdala") )
            cmoId = dao.getTermByTermName("amygdala ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("brain - caudate (basal ganglia)") )
            cmoId = dao.getTermByTermName("basal ganglion ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("brain - cerebellar hemisphere") )
            cmoId = dao.getTermByTermName("cerebellar hemisphere ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("brain - cerebellar hemisphere") )
            cmoId = dao.getTermByTermName("cerebellar hemisphere ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("brain - cerebellum") )
            cmoId = dao.getTermByTermName("cerebrum ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("brain - cortex") )
            cmoId = dao.getTermByTermName("cerebral cortex ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("brain - anterior cingulate cortex (ba24)"))
            cmoId = dao.getTermByTermName("Brodmann (1909) area 24 ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("brain - frontal cortex (ba9)"))
            cmoId = dao.getTermByTermName("Brodmann (1909) area 9 ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("brain - hippocampus"))
            cmoId = dao.getTermByTermName("hippocampus proper ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("brain - hypothalamus"))
            cmoId = dao.getTermByTermName("hypothalamus ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("brain - nucleus accumbens (basal ganglia)"))
            cmoId = dao.getTermByTermName("nucleus accumbens ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("brain - putamen (basal ganglia)"))
            cmoId = dao.getTermByTermName("putamen ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("brain - spinal cord (cervical c-1)"))
            cmoId = dao.getTermByTermName("C1 segment of cervical spinal cord ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("brain - substantia nigra"))
            cmoId = dao.getTermByTermName("substantia nigra ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("breast - mammary tissue"))
            cmoId = dao.getTermByTermName("mammary organ ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("cells - cultured fibroblasts"))
            cmoId = dao.getTermByTermName("cultured fibroblast ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("cells - ebv-transformed lymphocytes"))
            cmoId = dao.getTermByTermName("cultured EBV-transformed lymphocyte ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("cervix - ectocervix"))
            cmoId = dao.getTermByTermName("ectocervix ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("cervix - endocervix"))
            cmoId = dao.getTermByTermName("endocervix ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("colon - sigmoid"))
            cmoId = dao.getTermByTermName("colon ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("colon - transverse"))
            cmoId = dao.getTermByTermName("transverse colon ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("esophagus - gastroesophageal junction"))
            cmoId = dao.getTermByTermName("esophagogastric junction ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("esophagus - mucosa"))
            cmoId = dao.getTermByTermName("esophagus mucosa ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("esophagus - muscularis"))
            cmoId = dao.getTermByTermName("esophagus muscularis mucosa ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("heart - atrial appendage"))
            cmoId = dao.getTermByTermName("heart ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("heart - left ventricle"))
            cmoId = dao.getTermByTermName("heart left ventricle ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("kidney - cortex"))
            cmoId = dao.getTermByTermName("cortex of kidney ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("kidney - medulla"))
            cmoId = dao.getTermByTermName("renal medulla ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("muscle - skeletal"))
            cmoId = dao.getTermByTermName("skeletal muscle ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("nerve - tibial"))
            cmoId = dao.getTermByTermName("tibial nerve ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("pituitary"))
            cmoId = dao.getTermByTermName("pituitary gland ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("prostate"))
            cmoId = dao.getTermByTermName("prostate gland ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("skin - not sun exposed (suprapubic)"))
            cmoId = dao.getTermByTermName("suprapubic skin ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("skin - sun exposed (lower leg)"))
            cmoId = dao.getTermByTermName("lower leg skin ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("fallopian tube"))
            cmoId = dao.getTermByTermName("oviduct ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("small Intestine - terminal ileum"))
            cmoId = dao.getTermByTermName("small intestine ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("thyroid"))
            cmoId = dao.getTermByTermName("thyroid gland ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("whole blood"))
            cmoId = dao.getTermByTermName("blood ribonucleic acid composition measurement","CMO");
        else
            cmoId = dao.getTermByTermName(term,"CMO");

        return cmoId;
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

    public int getMapKey() {
        return mapKey;
    }

    public void setMapKey(int mapKey) {
        this.mapKey = mapKey;
    }

    public String getPhenoFile() {
        return phenoFile;
    }

    public void setPhenoFile(String phenoFile) {
        this.phenoFile = phenoFile;
    }

    public String getTpmFile() {
        return tpmFile;
    }

    public void setTpmFile(String tpmFile) {
        this.tpmFile = tpmFile;
    }

    public String getSampleFile() {
        return sampleFile;
    }

    public void setSampleFile(String sampleFile) {
        this.sampleFile = sampleFile;
    }
}

