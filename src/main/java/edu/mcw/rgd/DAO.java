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
import edu.mcw.rgd.process.mapping.MapManager;

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


    public int getSampleId(Sample sample) throws Exception{

        String sql = "Select * from Sample where age_days_from_dob_high_bound = "+sample.getAgeDaysFromHighBound()+ " and age_days_from_dob_low_bound = "+sample.getAgeDaysFromLowBound()+ " and" +
                " number_of_animals = "+sample.getNumberOfAnimals()+" and sex='" + sample.getSex()+"' and strain_ont_id = '"+sample.getStrainAccId()+"' and tissue_ont_id = '"+sample.getTissueAccId()+"'";
        SampleQuery sq = new SampleQuery(this.getDataSource(), sql);
        List<Sample> samples = sq.execute();
        if(samples == null || samples.isEmpty())
            return 0;
        else return samples.get(0).getId();
    }

    public int getExperimentId(Experiment e) throws Exception{

        String sql = "Select * from Experiment where experiment_name='"+e.getName()+"' and study_id="+e.getStudyId()+" and trait_ont_id='"+e.getTraitOntId()+"'";
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
}
