package edu.mcw.rgd;

import edu.mcw.rgd.datamodel.pheno.GeneExpressionRecordValue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class TpmManager {

    Logger log = LogManager.getLogger("status");

    DAO dao = new DAO();

    public void run() throws Exception {

        log.info("TpmManager starting ...");
        log.info("   "+dao.getConnectionInfo());

        List<Integer> expRecIds = dao.getExpRecIdsWithValues();
        int expRecCount = expRecIds.size();
        log.info("experiment record ids found: "+expRecCount);
        Collections.shuffle(expRecIds);

        AtomicInteger i = new AtomicInteger(0);
        expRecIds.parallelStream().forEach( expRecId -> {

            try {
                int ii = i.incrementAndGet();
                log.info(ii + "/" + expRecCount + ". processing EXP REC ID: " + expRecId);
                List<GeneExpressionRecordValue> values = dao.getGeneExpressionRecordValues(expRecId);

                Map<String, List<GeneExpressionRecordValue>> exprUnitMap = new HashMap<>();
                for (GeneExpressionRecordValue v : values) {
                    String exprUnit = v.getExpressionUnit();
                    List<GeneExpressionRecordValue> valueList = exprUnitMap.get(exprUnit);
                    if (valueList == null) {
                        valueList = new ArrayList<>();
                        exprUnitMap.put(exprUnit, valueList);
                    }
                    valueList.add(v);
                }

                for (Map.Entry<String, List<GeneExpressionRecordValue>> entry : exprUnitMap.entrySet()) {
                    run(entry.getKey(), entry.getValue());
                }
            } catch(Exception e) {
                throw new RuntimeException(e);
            }
        });
        log.info("TpmManager ended!");
    }

    void run( String exprUnit, List<GeneExpressionRecordValue> values ) throws Exception {

        int rid = values.get(0).getGeneExpressionRecordId();
        String prefix = "   RID:"+rid;

        // see if all values in TPM_VALUE column are null
        int nullValueCount = 0;
        double sum = 0.0;
        for( GeneExpressionRecordValue v: values ) {
            if( v.getTpmValue()==null ) {
                nullValueCount++;
            }
            sum += v.getExpressionValue();
        }
        if( nullValueCount==0 ) {
            log.info(prefix+"  "+exprUnit+" value count: "+values.size()+"    ALREADY PROCESSED!");
            return;
        }
        log.info(prefix+"  "+exprUnit+" value count: "+values.size());

        // for TPM processing, just copy TPM values
        if( exprUnit.equals("TPM") ) {
            for( GeneExpressionRecordValue v: values ) {
                v.setTpmValue(v.getExpressionValue());
            }
            dao.updateTpmValues(values);
        } else if( exprUnit.equals("FPKM") ) {

            double multiplier = 1000000 / sum;
            for (GeneExpressionRecordValue v : values) {
                v.setTpmValue(v.getExpressionValue() * multiplier);
            }
            dao.updateTpmValues(values);
        } else {
            throw new Exception("no support for expr unit "+exprUnit);
        }
    }
}
