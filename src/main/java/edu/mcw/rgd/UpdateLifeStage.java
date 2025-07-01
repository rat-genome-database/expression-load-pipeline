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
                    log.info("\t\tSample_id: " + s.getId() + "| AgeLow (in days): " + s.getAgeDaysFromLowBound()
                            + "| AgeHigh (in days): " + s.getAgeDaysFromHighBound()+" | Old Life Stage: " + s.getLifeStage() + " | New Life Stage: " + lifeStage);
                    s.setLifeStage(lifeStage);
                    updatedLS.add(s);;
                }
            }
            log.info("\tLife Stages being updated: " + updatedLS.size());
            dao.updateLifeStageBatch(updatedLS);

            log.info("Update Life Stage end!");
        }
        catch (Exception e){
            Utils.printStackTrace(e, log);
            throw e;
        }
    }

    String calcLifeStage(double ageLow, double ageHigh) throws Exception{
        /* for Rat
        * embryonic = <0 days (embryonic samples are entered with the "AGE_DAYS_FROM_DOB_LOW (or HIGH)_BOUND" as a negative number)
        * neonatal = 0 – 20 days
        * weanling = 21 – 34 days
        * juvenile  = 35 – 55 days
        * adult = 56 – 719 days
        * aged =  >=720 days
        * */
        String lsLow, lsHigh;
        String ls = "";

        if (ageLow>=720 && ageHigh>=720)
            ls = "aged";
        else if (ageLow>=56 && ageHigh>=720)
            ls= "adult;aged";
        else if (ageLow>=35 && ageHigh>=720)
            ls = "juvenile;adult;aged";
        else if (ageLow>=21 && ageHigh>=720)
            ls = "weanling;juvenile;adult;aged";
        else if (ageLow>=0 && ageHigh>=720)
            ls = "neonatal;weanling;juvenile;adult;aged";
        else if (ageLow<0 && ageHigh>=720)
            ls = "embryonic;neonatal;weanling;juvenile;adult;aged";
        else if (ageLow>=56 && ageHigh>=56)
            ls= "adult";
        else if (ageLow>=35 && ageHigh>=56)
            ls = "juvenile;adult";
        else if (ageLow>=21 && ageHigh>=56)
            ls = "weanling;juvenile;adult";
        else if (ageLow>=0 && ageHigh>=56)
            ls = "neonatal;weanling;juvenile;adult";
        else if (ageLow<0 && ageHigh>=56)
            ls = "embryonic;neonatal;weanling;juvenile;adult";
        else if (ageLow>=35 && ageHigh>=35)
            ls = "juvenile";
        else if (ageLow>=21 && ageHigh>=35)
            ls = "weanling;juvenile";
        else if (ageLow>=0 && ageHigh>=35)
            ls = "neonatal;weanling;juvenile";
        else if (ageLow<0 && ageHigh>=35)
            ls = "embryonic;neonatal;weanling;juvenile";
        else if (ageLow>=21 && ageHigh>=21)
            ls = "weanling";
        else if (ageLow>=0 && ageHigh>=21)
            ls = "neonatal;weanling";
        else if (ageLow<0 && ageHigh>=21)
            ls = "embryonic;neonatal;weanling";
        else if (ageLow>=0 && ageHigh>=0)
            ls = "neonatal";
        else if (ageLow<0 && ageHigh>=0)
            ls = "embryonic;neonatal";
        else if (ageLow<0 && ageHigh<0)
            ls = "embryonic";

        if (ls.isEmpty())
            return null;
        else
            return ls;
//
//        if (ageLow>=720)
//            lsLow = "aged";
//        else if (ageLow>=56)
//            lsLow = "adult";
//        else if (ageLow>=35)
//            lsLow ="juvenile";
//        else if (ageLow>=21)
//            lsLow = "weanling";
//        else if (ageLow>=0)
//            lsLow = "neonatal";
//        else
//            lsLow = "embryonic";
//
//        if (ageHigh>=720)
//            lsHigh = "aged";
//        else if (ageHigh>=56)
//            lsHigh = "adult";
//        else if (ageHigh>=35)
//            lsHigh ="juvenile";
//        else if (ageHigh>=21)
//            lsHigh = "weanling";
//        else if (ageHigh>=0)
//            lsHigh = "neonatal";
//        else
//            lsHigh = "embryonic";
//
//        if (Utils.stringsAreEqual(lsLow,lsHigh))
//            return lsLow;
//        else
//            return null; //lsHigh+";"+lsLow;
    }

    public void setSpecies(int species) {
        this.species = species;
    }

    public int getSpecies() {
        return species;
    }
}
