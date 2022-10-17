package edu.mcw.rgd;

import edu.mcw.rgd.dao.AbstractDAO;
import edu.mcw.rgd.dao.impl.*;
import edu.mcw.rgd.dao.spring.*;
import edu.mcw.rgd.datamodel.Gene;
import edu.mcw.rgd.datamodel.ontologyx.Term;
import edu.mcw.rgd.datamodel.pheno.Experiment;
import edu.mcw.rgd.datamodel.pheno.GeneExpressionRecord;
import edu.mcw.rgd.datamodel.pheno.GeneExpressionRecordValue;
import edu.mcw.rgd.datamodel.pheno.Sample;
import org.springframework.jdbc.object.BatchSqlUpdate;

import java.sql.Types;
import java.util.HashMap;
import java.util.List;


/**
 * @author hsnalabolu
 * wrapper to handle all DAO code
 */
public class DAO extends AbstractDAO {

    OntologyXDAO odao = new OntologyXDAO();
    GeneExpressionDAO gedao = new GeneExpressionDAO();
    XdbIdDAO xdbIdDAO = new XdbIdDAO();


    public String getConnectionInfo() {
        return odao.getConnectionInfo();
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
    public Sample getSampleFromBioSampleId(Sample sample) throws Exception{
        String sql = "Select * from Sample where biosample_id like '%"+sample.getBioSampleId()+"%'";


        System.out.println(sql);
        SampleQuery sq = new SampleQuery(this.getDataSource(), sql);
        List<Sample> samples = sq.execute();
        if(samples == null || samples.isEmpty())
            return null;
        else return samples.get(0);
    }

    public List<Sample> getSamplesBySpecies(int speciesType) throws Exception {
        String sql =  "select s.* from sample s where s.sample_id in (select distinct(r.sample_id) from experiment_record r where r.species_type_key=?)";
//        SampleQuery sq = new SampleQuery(this.getDataSource(), sql);
        List<Sample> samples = SampleQuery.execute2(this.getDataSource(),sql,speciesType);
        if (samples.isEmpty())
            return null;
        else return samples;
    }
    public void updateBioSampleId(int sampleId,Sample sample) throws Exception{

        Sample s = getSample(sample);
        String sql;
        if(s.getBioSampleId() != null) {
            sql  = "update Sample set biosample_id = '" + s.getBioSampleId() + ";" + sample.getBioSampleId() + "' where sample_id = " + sampleId;
        } else sql = "update Sample set biosample_id = '" + sample.getBioSampleId() + "' where sample_id = " + sampleId;
        this.update(sql);

    }
    public int getExperimentId(Experiment e) throws Exception{

        String sql = "Select * from Experiment where experiment_name='"+e.getName()+"' and study_id="+e.getStudyId();

        if(e.getTraitOntId() != null)
            sql += " and trait_ont_id='"+e.getTraitOntId()+"'";
        else sql += " and trait_ont_id is null";

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

    public int getGeneExprValueId(GeneExpressionRecordValue g) throws Exception{

        String sql = "Select * from Gene_expression_values where expressed_object_rgd_id="+g.getExpressedObjectRgdId()+" and expression_measurement_ont_id='"+g.getExpressionMeasurementAccId()+"' and gene_expression_exp_record_id="+g.getGeneExpressionRecordId()+
        " and expression_value="+g.getExpressionValue()+" and expression_unit='"+g.getExpressionUnit()+"' and map_key="+g.getMapKey();
        GeneExpressionRecordValueQuery sq = new GeneExpressionRecordValueQuery(this.getDataSource(), sql);
        List<GeneExpressionRecordValue> records = sq.execute();
        if(records == null || records.isEmpty())
            return 0;
        else return records.get(0).getId();
    }

    public void updateLifeStageBatch(List<Sample> samples) throws Exception{
        gedao.updateSampleLifeStageBatch(samples);
    }

    public int insertSample(Sample sample) throws Exception{
        return gedao.insertSample(sample);
    }

    public int insertExperiment(Experiment e) throws Exception{
        return gedao.insertExperiment(e);
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
        List<Gene> genes = xdbIdDAO.getActiveGenesByXdbId(xdbKey,ensembleId);
        if(genes == null || genes.isEmpty())
            return 0;
        else return genes.get(0).getRgdId();
    }

    public List<String> getSlims() throws Exception{
        OntologyXDAO odao = new OntologyXDAO();
        return odao.getAllSlimTerms("UBERON","AGR");
    }
    public void updateExpressionLevel() throws Exception{
        String sql = "update gene_expression_values set expression_level= 'below cutoff' where expression_level is null and expression_value < 0.5";
        this.update(sql);
        sql = "update gene_expression_values set expression_level= 'low' where expression_level is null and expression_value between 0.5 and 11";
        this.update(sql);
        sql = "update gene_expression_values set expression_level= 'medium' where expression_level is null and expression_value between 11 and 1000";
        this.update(sql);
        sql = "update gene_expression_values set expression_level= 'high' where expression_level is null and expression_value > 1000";
        this.update(sql);

    }
    public List<GeneExpressionRecordValue> getGeneExprRecordValuesBySlim(String unit,String termAcc,int mapKey,String level) throws Exception {
        String query = "select ge.* FROM gene_expression_values ge join gene_expression_exp_record gr on ge.gene_expression_exp_record_id = gr.gene_expression_exp_record_id" +
                " join sample s on s.sample_id = gr.sample_id join ont_terms t on t.term_acc = s.tissue_ont_id where  t.term_acc IN(SELECT child_term_acc FROM ont_dag where ont_rel_id != 'DE' START WITH parent_term_acc=?" +
                " CONNECT BY PRIOR child_term_acc=parent_term_acc ) AND t.is_obsolete=0 and ge.expression_unit =? and ge.map_Key = ? and ge.expression_level=?";


        GeneExpressionRecordValueQuery q = new GeneExpressionRecordValueQuery(getDataSource(), query);
        return execute(q, termAcc,unit,mapKey,level);
    }

    public void insertCounts(String term, HashMap<Integer,Integer> map, String level) throws Exception{
        String sql = "insert into gene_expression_value_counts(expressed_object_rgd_id,term_acc,expression_unit,expression_level,value_count)" +
                "values(?,?,'TPM',?,?)";
        BatchSqlUpdate su = new BatchSqlUpdate(this.getDataSource(), sql, new int[]{ Types.INTEGER,
                Types.VARCHAR, Types.VARCHAR,Types.INTEGER, },10000);
        su.compile();
        for(int id:map.keySet()){
            su.update(id,term,level,map.get(id));
        }
        su.flush();
    }

    public List<Integer> getExpRecIdsWithValues() throws Exception {
        String sql = "SELECT DISTINCT gene_expression_exp_record_id FROM gene_expression_values";
        return IntListQuery.execute(this, sql);
    }

    public List<GeneExpressionRecordValue> getGeneExpressionRecordValues(int geneExpressionRecordId) throws Exception {
        return gedao.getGeneExpressionRecordValues(geneExpressionRecordId);
    }

    public void updateTpmValues(List<GeneExpressionRecordValue> values) throws Exception {

        String sql = "UPDATE gene_expression_values SET tpm_value=? WHERE gene_expression_value_id=?";
        BatchSqlUpdate su = new BatchSqlUpdate(this.getDataSource(), sql, new int[]{ Types.DOUBLE, Types.INTEGER }, 10000);
        su.compile();
        for( GeneExpressionRecordValue v: values ) {
            su.update(v.getTpmValue(), v.getId());
        }
        su.flush();
    }
}
