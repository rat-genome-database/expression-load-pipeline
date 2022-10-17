package edu.mcw.rgd;

import edu.mcw.rgd.datamodel.pheno.Sample;
import edu.mcw.rgd.process.Utils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class UpdateLifeStage {

static Logger log = LogManager.getLogger("lsStatus");

    private DAO dao = new DAO();
    private int species;
    public void run() throws Exception {
        try {
            log.info("Updating Life Stage start!");
            log.info("   "+dao.getConnectionInfo());

            List<Sample> samples = dao.getSamplesBySpecies(species);
            List<Sample> updatedLS = new ArrayList<>();
            if (samples == null){
                log.info("No samples available");
                return;
            }
            int overlappingStages = 0;
            for (Sample s : samples){
                String lifeStage = calcLifeStage(s.getAgeDaysFromLowBound(), s.getAgeDaysFromHighBound());
                if (!Utils.stringsAreEqual(s.getLifeStage(),lifeStage)) {
                    log.info("\t\tSample_id: " + s.getId() + " | Old Life Stage: " + s.getLifeStage() + " | New Life Stage: " + lifeStage);
                    s.setLifeStage(lifeStage);
                    updatedLS.add(s);;
                }
                if (lifeStage==null) {
                    log.info("\t\t\tLife stage in question for sample_id=" + s.getId());
                    overlappingStages++;
                }
            }
            log.info("\tLife Stages being updated: " + updatedLS.size());
            log.info("\tOverlapping Life Stages: "+overlappingStages);
            dao.updateLifeStageBatch(updatedLS);

            log.info("Update Life Stage end!");
        }
        catch (Exception e){
            Utils.printStackTrace(e, log);
            throw e;
        }
    }

    String calcLifeStage(int ageLow, int ageHigh) throws Exception{
        /* for Rat
        * embryonic = <0 days (embryonic samples are entered with the "AGE_DAYS_FROM_DOB_LOW (or HIGH)_BOUND" as a negative number)
        * neonatal = 0 – 20 days
        * weanling = 21 – 34 days
        * juvenile  = 35 – 55 days
        * adult = 56 – 719 days
        * aged =  >=720 days
        * */
        String lsLow, lsHigh;
        if (ageLow>=720)
            lsLow = "aged";
        else if (ageLow>=56)
            lsLow = "adult";
        else if (ageLow>=35)
            lsLow ="juvenile";
        else if (ageLow>=21)
            lsLow = "weanling";
        else if (ageLow>=0)
            lsLow = "neonatal";
        else
            lsLow = "embryonic";

        if (ageHigh>=720)
            lsHigh = "aged";
        else if (ageHigh>=56)
            lsHigh = "adult";
        else if (ageHigh>=35)
            lsHigh ="juvenile";
        else if (ageHigh>=21)
            lsHigh = "weanling";
        else if (ageHigh>=0)
            lsHigh = "neonatal";
        else
            lsHigh = "embryonic";

        if (Utils.stringsAreEqual(lsLow,lsHigh))
            return lsLow;
        else
            return null; //lsHigh+"/"+lsLow;
    }

    public void setSpecies(int species) {
        this.species = species;
    }

    public int getSpecies() {
        return species;
    }
}
