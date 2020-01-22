package edu.mcw.rgd;

import edu.mcw.rgd.dao.AbstractDAO;
import edu.mcw.rgd.dao.impl.*;
import edu.mcw.rgd.dao.spring.*;
import edu.mcw.rgd.datamodel.MappedOrtholog;
import edu.mcw.rgd.datamodel.RgdId;
import edu.mcw.rgd.datamodel.ontologyx.Term;
import edu.mcw.rgd.datamodel.pheno.Experiment;
import edu.mcw.rgd.datamodel.pheno.GeneExpressionRecord;
import edu.mcw.rgd.datamodel.pheno.GeneExpressionRecordValue;
import edu.mcw.rgd.datamodel.pheno.Sample;
import edu.mcw.rgd.process.Utils;
import edu.mcw.rgd.process.mapping.MapManager;
import org.springframework.jdbc.core.SqlParameter;

import java.sql.Types;
import java.util.List;


/**
 * @author hsnalabolu
 * wrapper to handle all DAO code
 */
public class DAO extends AbstractDAO {



    OntologyXDAO odao = new OntologyXDAO();
    PhenominerDAO phenominerDAO = new PhenominerDAO();
    GeneExpressionDAO gedao = new GeneExpressionDAO();
    XdbIdDAO xdbIdDAO = new XdbIdDAO();


    public String getConnectionInfo() {
        return phenominerDAO.getConnectionInfo();
    }


    public Sample getSample(Sample sample) throws Exception{
        String sql = "Select * from Sample where number_of_animals = "+sample.getNumberOfAnimals()+" and strain_ont_id";


        if(sample.getStrainAccId() != null)
            sql += "= '"+sample.getStrainAccId()+"'";
        else sql += " is null";

        if(sample.getTissueAccId() != null)
            sql += " and tissue_ont_id = '"+sample.getTissueAccId()+ "'";
        else sql += " and tissue_ont_id is null";

        if(sample.getCellTypeAccId() != null)
            sql += " and cell_type_ont_id = '"+sample.getCellTypeAccId()+"'";
        else sql += " and cell_type_ont_id is null";


        if(sample.getSex() != null)
            sql += " and sex='" + sample.getSex() + "'";
        else sql += " and sex is null";

        if(sample.getAgeDaysFromHighBound() != null)
            sql += " and age_days_from_dob_high_bound = "+sample.getAgeDaysFromHighBound();
        else sql += " and age_days_from_dob_high_bound is null";

        if(sample.getAgeDaysFromLowBound() != null)
            sql += " and age_days_from_dob_low_bound = "+sample.getAgeDaysFromLowBound();
        else sql += " and age_days_from_dob_high_bound is null";

        if(sample.getNotes() != null && !sample.getNotes().isEmpty()) {
            String notes = sample.getNotes();
            notes = notes.replaceAll("'","''");
            sql += " and dbms_lob.compare(sample_notes, '" + notes + "') = 0";
        }


        SampleQuery sq = new SampleQuery(this.getDataSource(), sql);


       System.out.println(sql);

        List<Sample> samples = sq.execute();
        if(samples == null || samples.isEmpty())
            return null;
        else return samples.get(0);
    }
    public Sample getSampleFromBioSampleId(String id) throws Exception{
        String sql = "Select * from Sample where biosample_id like '%"+id+"%'";
        SampleQuery sq = new SampleQuery(this.getDataSource(), sql);
        List<Sample> samples = sq.execute();
        if(samples == null || samples.isEmpty())
            return null;
        else return samples.get(0);
    }
    public void updateBioSampleId(int sampleId,Sample sample) throws Exception{
        System.out.println(sample.getTissueAccId());
        String sql;
         sql = "update Sample set tissue_ont_id = '" + sample.getTissueAccId() + "' where sample_id = " + sampleId;
        this.update(sql);

    }
    public int getExperimentId(Experiment e) throws Exception{

        String sql = "Select * from Experiment where experiment_name='"+e.getName()+"' and study_id="+e.getStudyId()+" and trait_ont_id='"+e.getTraitOntId()+"'";


        System.out.println(sql);
        ExperimentQuery sq = new ExperimentQuery(this.getDataSource(), sql);
        List<Experiment> experiments = sq.execute();
        if(experiments == null || experiments.isEmpty())
            return 0;
        else return experiments.get(0).getId();
    }

    public int getGeneExprRecordId(GeneExpressionRecord g) throws Exception{

        String sql = "Select * from Gene_expression_exp_record where experiment_id="+g.getExperimentId()+" and sample_id="+g.getSampleId()+" and species_type_key="+g.getSpeciesTypeKey();
        GeneExpressionRecordQuery sq = new GeneExpressionRecordQuery(this.getDataSource(), sql);
        List<GeneExpressionRecord> records = sq.execute();
        if(records == null || records.isEmpty())
            return 0;
        else return records.get(0).getId();
    }

    public int getGeneExprRecordId(Sample s) throws Exception{

        String sql = "Select * from Gene_expression_exp_record where sample_id="+s.getId();
        GeneExpressionRecordQuery sq = new GeneExpressionRecordQuery(this.getDataSource(), sql);
        List<GeneExpressionRecord> records = sq.execute();
        if(records == null || records.isEmpty())
            return 0;
        else return records.get(0).getId();
    }
    public int getGeneExprValueId(GeneExpressionRecordValue g) throws Exception{

        String sql = "Select * from Gene_expression_values where expressed_object_rgd_id="+g.getExpressedObjectRgdId()+" and expression_measurement_ont_id='"+g.getExpressionMeasurementAccId()+"' and gene_expression_exp_record_id="+g.getGeneExpressionRecordId()+
        " and expression_value="+g.getExpressionValue()+" and expression_unit='"+g.getExpressionUnit()+"' and map_key="+g.getMapKey();
        GeneExpressionRecordValueQuery sq = new GeneExpressionRecordValueQuery(this.getDataSource(), sql);
        List<GeneExpressionRecordValue> records = sq.execute();
        if(records == null || records.isEmpty())
            return 0;
        else return records.get(0).getId();
    }
    public int insertSample(Sample sample) throws Exception{
        return phenominerDAO.insertSample(sample);
    }

    public int insertExperiment(Experiment e) throws Exception{
        return phenominerDAO.insertExperiment(e);
    }

    public int insertGeneExpressionRecord(GeneExpressionRecord g) throws Exception {
        return gedao.insertGeneExpressionRecord(g);
    }

    public int insertGeneExpressionRecordValue(GeneExpressionRecordValue g) throws Exception{
        return gedao.insertGeneExpressionRecordValue(g);
    }

    public String getTermByTermName(String term,String ontID) throws Exception {
        Term t =  odao.getTermByTermName(term,ontID);
        if(t != null)
            return t.getAccId();
        else return null;
    }

    public int getRGDIdsByXdbId(int xdbKey, String ensembleId) throws Exception{
        List<RgdId> rgdIds = xdbIdDAO.getRGDIdsByXdbId(xdbKey,ensembleId);
        if(rgdIds == null || rgdIds.isEmpty())
            return 0;
        else return rgdIds.get(0).getRgdId();
    }

    public void updateExpressionLevel() throws Exception{
        String sql = "update gene_expression_values set expression_level= 'below cutoff' where expression_level is null and expression_value < 0.5";
        this.update(sql);
        sql = "update gene_expression_values set expression_level= 'low' where expression_level is null and expression_value between 0.5 and 10";
        this.update(sql);
        sql = "update gene_expression_values set expression_level= 'medium' where expression_level is null and expression_value between 11 and 1000";
        this.update(sql);
        sql = "update gene_expression_values set expression_level= 'high' where expression_level is null and expression_value > 1000";
        this.update(sql);
    }
}
