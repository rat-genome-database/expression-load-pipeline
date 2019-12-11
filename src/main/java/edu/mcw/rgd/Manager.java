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
    private boolean firstRun;

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
                int ageHigh = 0;
                int ageLow = 0;
             /*   if(!headerVal.contains("Sample Characteristic[sex]") && noOfRuns == 0 ) {
                    sample.setSex("manual");
                    sample.setAgeDaysFromHighBound(null);
                    sample.setAgeDaysFromLowBound(null);
                }
                else {
            */        if (headerIndex.containsKey("Sample Characteristic[sex]")) {
                    sex = cols[headerIndex.get("Sample Characteristic[sex]")];
                    if (sex.equalsIgnoreCase("unknown") || sex.equalsIgnoreCase("not available"))
                        sex = "not specified";
                    sample.setSex(sex);
                }else sample.setSex("not specified");
                        if (headerIndex.containsKey("Sample Characteristic[age]") || headerIndex.containsKey("Sample Characteristic[developmental stage]")) {
                            ageHigh = getAgeHigh(cols, headerIndex);
                            ageLow = getAgeLow(cols, headerIndex);
                            sample.setAgeDaysFromHighBound(ageHigh);
                            sample.setAgeDaysFromLowBound(ageLow);
                        } else {
                            sample.setAgeDaysFromHighBound(null);
                            sample.setAgeDaysFromLowBound(null);
                        }


            //    }
                //Strian doesnt exist for human it exists for rat
                if(headerIndex.containsKey("Sample Characteristic[strain]")) {
                    String strain = cols[headerIndex.get("Sample Characteristic[strain]")];
                    String strainOntId = (String) (getStrainOntIds().get(strain));
                    sample.setStrainAccId(strainOntId);
                } else sample.setStrainAccId(null);

                String part;
                if(headerIndex.containsKey("Sample Characteristic[organism part]"))
                    part = cols[headerIndex.get("Sample Characteristic[organism part]")];
                else part = cols[headerIndex.get("Sample Characteristic[sampling site]")];

                part = part.replace("'","");

                String tissue;
                if(headerIndex.containsKey("Sample Characteristic Ontology Term[organism part]"))
                    tissue = cols[headerIndex.get("Sample Characteristic Ontology Term[organism part]")];
                else tissue = cols[headerIndex.get("Sample Characteristic Ontology Term[sampling site]")];
                String tissueOntId;
                if(tissue != null && tissue.contains("http://purl.obolibrary.org/obo/")) {
                    tissueOntId  = tissue.split("http://purl.obolibrary.org/obo/")[1];
                    tissueOntId = tissueOntId.replace("_", ":");
                }else tissueOntId = part;

                if(headerVal.contains("Sample Characteristic[cell line]")) {
                    String cellLine = cols[headerIndex.get("Sample Characteristic[cell line]")];
                        String strainOntId = dao.getTermByTermName(cellLine,"CVCL");
                        if(strainOntId == null)
                            sample.setStrainAccId(cellLine);
                        else sample.setStrainAccId(strainOntId);

                }
                if(headerVal.contains("Sample Characteristic[cell type]") && headerIndex.containsKey("Sample Characteristic Ontology Term[cell type]")) {
                    String cellType = cols[headerIndex.get("Sample Characteristic Ontology Term[cell type]")];
                    String cellTypeOntId="";
                    if(!cellType.isEmpty()) {
                        if (cellType.contains("EFO"))
                            cellTypeOntId = cellType.split("http://www.ebi.ac.uk/efo/")[1];
                        else cellTypeOntId = cellType.split("http://purl.obolibrary.org/obo/")[1];
                        cellTypeOntId = cellTypeOntId.replace("_", ":");
                        sample.setCellTypeAccId(cellTypeOntId);
                    }else sample.setCellTypeAccId(cols[headerIndex.get("Sample Characteristic[cell type]")]);
                }


                experiment.setStudyId(getStudyId());
                experiment.setName(getExperimentName(part));
                experiment.setTraitOntId(getTraitId(part,getExperimentName(part)));
                experiments.add(experiment);


                    experimentId = dao.getExperimentId(experiment);
                    if(experimentId == 0) {
                        experimentId = dao.insertExperiment(experiment);
                        System.out.println("Inserted experiment :" + experimentId);
                    }
                    log.info("Inserted experiment :" + experimentId);


                experiment.setId(experimentId);


                sample.setNumberOfAnimals(getNoOfRuns());

                sample.setTissueAccId(tissueOntId);
                sample.setBioSampleId(cols[headerIndex.get("Run")]);
                samples.add(sample);

                Sample s;


                sample.setNotes(getNotes(cols,headerIndex));


                if(firstRun == false)
                    s = dao.getSampleFromBioSampleId(sample);
                else
                     s = dao.getSample(sample);

                    if(s == null) {
                        sampleId = dao.insertSample(sample);
                        System.out.println("Inserted Sample :" + sampleId);
                    } else {
                        sampleId = s.getId();
                        if(s.getBioSampleId() == null || !s.getBioSampleId().contains(sample.getBioSampleId()))
                        {
                            dao.updateBioSampleId(sampleId,sample);
                            System.out.println("Updated Sample :" + sampleId);
                        }
                    }
                   // log.info("Inserted Sample :" + sampleId);


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
                    geneExpressionRecord.setSpeciesTypeKey(MapManager.getInstance().getMap(mapKey).getSpeciesTypeKey());

                    int geneExprRecId = dao.getGeneExprRecordId(geneExpressionRecord);
                    if(geneExprRecId == 0) {
                        geneExprRecId = dao.insertGeneExpressionRecord(geneExpressionRecord);
                        System.out.println("Inserted geneExpressionRecord :" + geneExprRecId);
                    }
                    geneExpressionRecord.setId(geneExprRecId);
                    log.info("Inserted geneExpressionRecord :" + geneExprRecId);


                geneExpressionRecordMap.put(geneExprRecId,header);
                geneExpressionRecords.put(geneExprRecId,geneExpressionRecord);

                String cmoId = getCMOId(part);

                cmoIDs.put(header,cmoId);
            }

        }

        logSummary.info("Experiments Inserted : " + experiments.size());
        logSummary.info("Samples Inserted : " + samples.size());
        logSummary.info("Gene Expression Records Inserted : " + geneExpressionRecords.size());
       reader.close();
       loadTPMValues();
      dao.updateExpressionLevel();
    }

    public String getNotes(String[] cols, Map<String,Integer> headerIndex){
        String notes = "";
        if(headerVal.contains("Factor Value[disease]") )
            notes = (cols[headerIndex.get("Factor Value[disease]")]);

        if(headerVal.contains("Factor Value[individual]")) {
            if(!notes.isEmpty())
                notes += ",";
            else notes = "";
            notes += cols[headerIndex.get("Factor Value[individual]")];

        }
        if(headerVal.contains("Factor Value[ancestry category]")) {

            if(!notes.isEmpty())
                notes += ",";
            else notes = "";
            notes += cols[headerIndex.get("Factor Value[ancestry category]")];

        }
        if(headerVal.contains("Factor Value[growth condition]")) {

            if(!notes.isEmpty())
                notes += ",";
            else notes = "";
            notes += cols[headerIndex.get("Factor Value[growth condition]")];

        }
        return notes;
    }
    public int getAgeHigh(String[] cols, Map<String,Integer> headerIndex) {

        int ageHigh = 0;
        if(headerVal.contains("Sample Characteristic[developmental stage]")) {
            String developage = cols[headerIndex.get("Sample Characteristic[developmental stage]")];
            if(developage.contains("week post conception")) {
                ageHigh = Integer.valueOf(developage.split("week")[0].trim());
                ageHigh = ageHigh * 7;
                ageHigh = ageHigh - 280;
            }else if(developage.contains("post conception weeks")) {
                String col = developage.split("post conception")[0].trim();
                col = col.replaceAll("[^\\d]","");
                ageHigh = Integer.valueOf(col.trim());
                ageHigh = ageHigh * 7;
                ageHigh = ageHigh - 280;
            }else if(developage.contains("adolescent")) {
                ageHigh = 17 * 365;
            }else if(developage.contains("elderly")){
                ageHigh = 58 * 365;
            } else if(developage.contains("infant")) {
                ageHigh = 271;
            }else if(developage.contains("middle adult")){
                ageHigh = 55 * 365;
            }else if(developage.contains("neonate")){
                ageHigh = 34;
            }else if(developage.contains("school age child")){
                ageHigh = 8 * 365;
            }else if(developage.contains("toddler"))
                ageHigh = 4 * 365;
             else if(developage.contains("young adult"))
                ageHigh = 39 * 365;
            else if(developage.contains("adult"))
                ageHigh = 92 * 365;
            else if(developage.contains("fetal"))
                ageHigh = 40 * 7;
            else if(developage.contains("juvenile"))
                ageHigh = 15 * 365;
            else if(developage.contains("Carnegie Stage")) {
                ageHigh = Integer.valueOf(developage.split("Carnegie Stage")[1].trim());
                if(ageHigh == 13)
                    ageHigh = -248;
                else {
                    int diff = ageHigh - 13;
                    diff = diff * 3;
                    ageHigh = -248 - diff;
                }
            }
            return ageHigh;
        }
        String age = cols[headerIndex.get("Sample Characteristic[age]")];
        if (age.contains("week")) {
            ageHigh = Integer.valueOf(age.split("week")[0].trim());
            ageHigh = ageHigh * 7;
        }else if (age.contains("month")) {
            ageHigh = Integer.valueOf(age.split("month")[0].trim());
            ageHigh = ageHigh * 30;
        }
        else if (age.contains("day")) {
            if (cols[headerIndex.get("Sample Characteristic[developmental stage]")].equalsIgnoreCase("embryo")) {
                ageHigh = Integer.valueOf(age.split("day")[0].trim()) - 21;
            } else if (cols[headerIndex.get("Sample Characteristic[developmental stage]")].equalsIgnoreCase("postnatal")) {
                ageHigh = Integer.valueOf(age.split("day")[0].trim());
            }
        }
        if (age.contains("year")) {
            String ageSplit = age.split("year")[0].trim();
            if(ageSplit.contains("to")) {
               String[] ages  = ageSplit.split("to");
               ageHigh = Integer.valueOf(ages[1].trim());
            }else ageHigh = Integer.valueOf(ageSplit);

            ageHigh = ageHigh * 365;
        }
        return ageHigh;
    }
    public int getAgeLow(String[] cols, Map<String,Integer> headerIndex) {

        int ageLow = 0;
        if(headerVal.contains("Sample Characteristic[developmental stage]")) {
            String developage = cols[headerIndex.get("Sample Characteristic[developmental stage]")];
            if(developage.contains("week post conception")) {
                ageLow = Integer.valueOf(developage.split("week")[0].trim());
                ageLow = ageLow * 7;
                ageLow = ageLow - 280;
            }else if(developage.contains("post conception weeks")) {
                String col = developage.split("post conception")[0].trim();
                col = col.replaceAll("[^\\d.]","");
                ageLow = Integer.valueOf(col.trim());

                ageLow = ageLow * 7;
                ageLow = ageLow - 280;
            }else if(developage.contains("adolescent")) {
                ageLow = 13 * 365;
            }else if(developage.contains("elderly")){
                ageLow = 55 * 365;
            } else if(developage.contains("infant")) {
                ageLow = 127;
            }else if(developage.contains("middle adult")){
                ageLow = 46 * 365;
            }else if(developage.contains("neonate")){
                ageLow = 0;
            }else if(developage.contains("school age child")){
                ageLow = 7 * 365;
            }else if(developage.contains("toddler"))
                ageLow = 1 * 365;
            else if(developage.contains("young adult"))
                ageLow = 28 * 365;
            else if(developage.contains("adult"))
                ageLow = 14 * 365;
            else if(developage.contains("fetal"))
                ageLow = 16 * 7;
            else if(developage.contains("juvenile"))
                ageLow = 4 * 365;
            else if(developage.contains("Carnegie Stage")) {
                ageLow = Integer.valueOf(developage.split("Carnegie Stage")[1].trim());
                if(ageLow == 13)
                    ageLow = -252;
                else {
                    int diff = ageLow - 13;
                    diff = diff * 3;
                    ageLow = -252 - diff;
                }
            }
            return ageLow;
        }
        String age = cols[headerIndex.get("Sample Characteristic[age]")];
        if (age.contains("week")) {
            ageLow = Integer.valueOf(age.split("week")[0].trim());
            ageLow = ageLow * 7;
        }else if (age.contains("month")) {
            ageLow = Integer.valueOf(age.split("month")[0].trim());
            ageLow = ageLow * 30;
        }else if (age.contains("day")) {
            if (cols[headerIndex.get("Sample Characteristic[developmental stage]")].equalsIgnoreCase("embryo")) {
                ageLow = Integer.valueOf(age.split("day")[0].trim()) - 23;
            } else if (cols[headerIndex.get("Sample Characteristic[developmental stage]")].equalsIgnoreCase("postnatal")) {
                ageLow = Integer.valueOf(age.split("day")[0].trim());
            }
        }
        if (age.contains("year")) {
            String ageSplit = age.split("year")[0].trim();
            if(ageSplit.contains("to")) {
                String[] ages  = ageSplit.split("to");
                ageLow = Integer.valueOf(ages[0].trim());
            }else ageLow = Integer.valueOf(ageSplit);
            ageLow = ageLow * 365;
        }
        return ageLow;
    }

    public String getExperimentName(String part) throws Exception{

        System.out.println(part);
        String exprName = part + " molecular composition trait";
        if(part.equalsIgnoreCase("forebrain") || part.equalsIgnoreCase("hindbrain") || part.equalsIgnoreCase("amygdala") || part.equalsIgnoreCase("brain meninx")
           || part.equalsIgnoreCase("Brodmann (1909) area 24") || part.equalsIgnoreCase("Brodmann (1909) area 9") || part.equalsIgnoreCase("caudate nucleus") || part.equalsIgnoreCase("cerebellar hemisphere")
           || part.equalsIgnoreCase("cerebral cortex") || part.equalsIgnoreCase("diencephalon") || part.equalsIgnoreCase("dorsal thalamus") || part.equalsIgnoreCase("globus pallidus")
           || part.equalsIgnoreCase("hippocampal formation") || part.equalsIgnoreCase("hippocampus proper") || part.equalsIgnoreCase("hypothalamus") || part.equalsIgnoreCase("locus ceruleus")
           || part.equalsIgnoreCase("medulla oblongata") || part.equalsIgnoreCase("middle frontal gyrus") || part.equalsIgnoreCase("middle temporal gyrus") || part.equalsIgnoreCase("nucleus accumbens")
           || part.equalsIgnoreCase("occipital cortex") || part.equalsIgnoreCase("occipital lobe") || part.equalsIgnoreCase("parietal lobe") || part.equalsIgnoreCase("pineal body")
           || part.equalsIgnoreCase("pituitary gland") || part.equalsIgnoreCase("putamen") || part.equalsIgnoreCase("substantia nigra") || part.equalsIgnoreCase("Brodmann area")
           || part.equalsIgnoreCase("basal ganglion")  || part.equalsIgnoreCase("choroid plexus")  || part.equalsIgnoreCase("telencephalon") || part.equalsIgnoreCase("pons")
           || part.equalsIgnoreCase("midbrain") || part.equalsIgnoreCase("forebrain fragment") || part.equalsIgnoreCase("diencephalon and midbrain") || part.equalsIgnoreCase("forebrain and midbrain")
           || part.equalsIgnoreCase("hindbrain without cerebellum") || part.equalsIgnoreCase("pituitary and diencephalon") || part.equalsIgnoreCase("brain fragment") || part.equalsIgnoreCase("hindbrain fragment")
                || part.equalsIgnoreCase("hippocampus"))

            exprName = "brain molecular composition trait";
        else if(part.equalsIgnoreCase("skeletal muscle tissue") || part.equalsIgnoreCase("smooth muscle tissue") || part.equalsIgnoreCase("diaphragm") || part.equalsIgnoreCase("muscle of arm")
                || part.equalsIgnoreCase("muscle of leg") || part.equalsIgnoreCase("skeletal muscle of trunk") || part.equalsIgnoreCase("skeletal muscle organ") || part.equalsIgnoreCase("musculature")){
            exprName = "muscle molecular composition trait";
        }else if(part.equalsIgnoreCase("adipose tissue") || part.equalsIgnoreCase("subcutaneous adipose tissue")){
            exprName = "adipose molecular composition trait";
        }else if(part.equalsIgnoreCase("breast")) {
            exprName = "mammary gland morphology trait";
        }else if(part.equalsIgnoreCase("prostate gland")) {
            exprName = "prostate morphology trait";
        }else if(part.equalsIgnoreCase("sigmoid colon")) {
            exprName = "colon morphology trait";
        } else if (part.equalsIgnoreCase("frontal lobe") || part.equalsIgnoreCase("temporal lobe") || part.equalsIgnoreCase("prefrontal cortex") || part.equalsIgnoreCase("cerebellum")){
            exprName = "cerebrum morphology trait";
        } else if(part.equalsIgnoreCase("saliva-secreting gland")){
            exprName = "salivary gland morphology trait";
        }else if(part.equalsIgnoreCase("bone marrow")){
            exprName = "bone marrow cell morphology trait";
        }else if(part.equalsIgnoreCase("duodenum")){
            exprName = "small intestine morphology trait";
        }else if(part.equalsIgnoreCase("endometrium")){
            exprName = "uterus ribonucleic acid amount";
        }else if(part.equalsIgnoreCase("fallopian tube")){
            exprName = "oviduct morphology trait";
        }else if(part.equalsIgnoreCase("ectocervix") || part.equalsIgnoreCase("endocervix") || part.equalsIgnoreCase("uterine cervix") || part.equalsIgnoreCase("vagina")
                || part.equalsIgnoreCase("vulva") || part.equalsIgnoreCase("mammary")){
            exprName = "female reproductive system morphology trait";
        }else if(part.equalsIgnoreCase("epididymis") || part.equalsIgnoreCase("penis") || part.equalsIgnoreCase("seminal vesicle") || part.equalsIgnoreCase("vas deferens")){
            exprName = "male reproductive system morphology trait";
        }else if(part.equalsIgnoreCase("vermiform appendix") || part.equalsIgnoreCase("esophagogastric junction") || part.equalsIgnoreCase("large intestine")
                || part.equalsIgnoreCase("transverse colon") || part.equalsIgnoreCase("esophagus mucosa") || part.equalsIgnoreCase("esophagus muscularis mucosa")
                || part.equalsIgnoreCase("greater omentum") || part.equalsIgnoreCase("minor salivary gland") || part.equalsIgnoreCase("mouth mucosa")
                || part.equalsIgnoreCase("parotid gland") || part.equalsIgnoreCase("submandibular gland")){
            exprName = "gastrointestinal system morphology trait";
        }else if(part.equalsIgnoreCase("zone of skin") || part.equalsIgnoreCase("transformed skin fibroblast") || part.equalsIgnoreCase("lower leg skin") || part.equalsIgnoreCase("suprapubic skin")
                || part.equalsIgnoreCase("hair follicle")){
            exprName = "skin molecular composition trait";
        }else if(part.equalsIgnoreCase("aorta") || part.equalsIgnoreCase("coronary artery") || part.equalsIgnoreCase("tibial artery")){
            exprName = "artery molecular composition trait";
        }else if(part.equalsIgnoreCase("cortex of kidney") || part.equalsIgnoreCase("left renal pelvis") || part.equalsIgnoreCase("renal pelvis") || part.equalsIgnoreCase("right renal pelvis")
                || part.equalsIgnoreCase("right renal cortex") || part.equalsIgnoreCase("left renal cortex") || part.equalsIgnoreCase("left kidney")
                || part.equalsIgnoreCase("right kidney")){
            exprName = "kidney molecular composition trait";
        }else if(part.equalsIgnoreCase("atrium auricular region") || part.equalsIgnoreCase("heart left ventricle") || part.equalsIgnoreCase("left cardiac atrium") || part.equalsIgnoreCase("mitral valve")
                || part.equalsIgnoreCase("pulmonary valve") || part.equalsIgnoreCase("tricuspid valve") ){
            exprName = "heart molecular composition trait";
        }else if(part.equalsIgnoreCase("C1 segment of cervical spinal cord")){
            exprName = "spinal cord molecular composition trait";
        }else if(part.equalsIgnoreCase("blood") || part.equalsIgnoreCase("umbilical cord blood") || part.equalsIgnoreCase("venous blood") ){
            exprName = "hematopoietic system morphology trait";
        }else if(part.equalsIgnoreCase("EBV-transformed lymphocyte")){
            exprName = "lymphocyte morphology trait";
        }else if(part.equalsIgnoreCase("dura mater")){
            exprName = "meninges morphology trait";
        }else if(part.equalsIgnoreCase("tibial nerve") || part.equalsIgnoreCase("peripheral nervous system")){
            exprName = "nervous system morphology trait";
        }else if(part.equalsIgnoreCase("small intestine Peyers patch")){
            exprName = "Peyers patch morphology trait";
            return exprName;
        }else if(part.equalsIgnoreCase("trachea") || part.equalsIgnoreCase("olfactory apparatus") || part.equalsIgnoreCase("pleura")){
            exprName = "respiratory system morphology trait";
        }else if(part.equalsIgnoreCase("throat") || part.equalsIgnoreCase("chordate pharynx")){
            exprName = "pharynx morphology trait";
        }else if(part.equalsIgnoreCase("distal gut") || part.equalsIgnoreCase("proximal gut") || part.equalsIgnoreCase("terminal ileum") || part.equalsIgnoreCase("ileum")
                || part.equalsIgnoreCase("caecum")){
            exprName = "intestine morphology trait";
        }else if(part.equalsIgnoreCase("oral cavity")){
            exprName = "mouth morphology trait";
        }else if(part.equalsIgnoreCase("cartilage tissue")){
            exprName = "cartilage morphology trait";
        }else if(part.equalsIgnoreCase("bone tissue")){
            exprName = "bone morphology trait";
        }else if(part.equalsIgnoreCase("epithelium of bronchus")){
            exprName = "lung molecular composition trait";
        }else if(part.equalsIgnoreCase("mucosa")) {
            exprName = "vertebrate trait";
        }

        String traitId = dao.getTermByTermName(exprName,"VT");
        if(traitId == null)
             exprName = part + " morphology trait";


            return exprName;
    }
    public String getTraitId(String part, String exprName) throws Exception{

        String traitId = null;
        if(exprName.equalsIgnoreCase("Peyers patch morphology trait")) {
            traitId = "VT:0000696";
            return traitId;
        }
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
        if(part.equalsIgnoreCase("diencephalon and midbrain") || part.equalsIgnoreCase("forebrain and midbrain") || part.equalsIgnoreCase("hindbrain without cerebellum")
                || part.equalsIgnoreCase("pituitary and diencephalon"))
            term = "combined " + term;

        if(part.equalsIgnoreCase("forebrain") || part.equalsIgnoreCase("hindbrain") || part.equalsIgnoreCase("brain fragment") || part.equalsIgnoreCase("midbrain"))
            cmoId = dao.getTermByTermName("brain ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("frontal lobe") || part.equalsIgnoreCase("temporal lobe") || part.equalsIgnoreCase("prefrontal cortex") || part.equalsIgnoreCase("cerebellum"))
            cmoId = dao.getTermByTermName("cerebrum ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("skeletal muscle tissue"))
            cmoId = dao.getTermByTermName("skeletal muscle ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("smooth muscle tissue"))
            cmoId = dao.getTermByTermName("smooth muscle ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("breast"))
            cmoId = dao.getTermByTermName("mammary organ ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("leukocyte"))
            cmoId = dao.getTermByTermName("white blood cell ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("sigmoid colon"))
            cmoId = dao.getTermByTermName("colon ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("duodenum"))
            cmoId = dao.getTermByTermName("small intestine ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("endometrium"))
            cmoId = dao.getTermByTermName("uterus ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("fallopian tube"))
            cmoId = dao.getTermByTermName("oviduct ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("saliva-secreting gland"))
            cmoId = dao.getTermByTermName("salivary gland ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("vermiform appendix"))
            cmoId = dao.getTermByTermName("appendix ribonucleic acid composition measurement","CMO");
        else if (part.equalsIgnoreCase("zone of skin"))
            cmoId = dao.getTermByTermName("skin ribonucleic acid composition measurement","CMO");
        else if(part.equalsIgnoreCase("forebrain fragment"))
            cmoId = dao.getTermByTermName("forebrain ribonucleic acid composition measurement","CMO");
        else if(part.equalsIgnoreCase("hindbrain fragment"))
            cmoId = dao.getTermByTermName("hindbrain ribonucleic acid composition measurement","CMO");
        else if(part.equalsIgnoreCase("hippocampus"))
            cmoId = dao.getTermByTermName("hippocampus proper ribonucleic acid composition measurement","CMO");
        else
            cmoId = dao.getTermByTermName(term,"CMO");

        return cmoId;
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

    public boolean isFirstRun() {
        return firstRun;
    }

    public void setFirstRun(boolean firstRun) {
        this.firstRun = firstRun;
    }
}

