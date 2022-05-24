package edu.mcw.rgd;

import edu.mcw.rgd.datamodel.pheno.Sample;
import org.springframework.jdbc.object.MappingSqlQuery;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;

public class SampleQuery extends MappingSqlQuery {

    public SampleQuery(DataSource ds, String query) {
        super(ds, query);
    }

    protected Object mapRow(ResultSet rs, int rowNum) throws SQLException {

        Sample sample = new Sample();
        sample.setId(rs.getInt("sample_id"));
        sample.setAgeDaysFromHighBound(rs.getInt("age_days_from_dob_high_bound"));
        sample.setAgeDaysFromLowBound(rs.getInt("age_days_from_dob_low_bound"));
        sample.setNumberOfAnimals(rs.getInt("number_of_animals"));
        sample.setNotes(rs.getString("sample_notes"));
        sample.setSex(rs.getString("sex"));
        sample.setStrainAccId(rs.getString("strain_ont_id"));
        sample.setTissueAccId(rs.getString("tissue_ont_id"));
        sample.setCellTypeAccId(rs.getString("cell_type_ont_id"));
        sample.setGeoSampleAcc(rs.getString("geo_sample_acc"));
        sample.setBioSampleId(rs.getString("biosample_id"));

        if( true ) {
            throw new SQLException("TODO: column 'subcell_component_ont_id' has  to be implemented in Sample object");
            //sample.setSubcellComponentAccId(rs.getString("subcell_component_ont_id"));
        }

        return sample;
    }

}
