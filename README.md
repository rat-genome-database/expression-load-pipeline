# expression-load-pipeline
Pipleine to load TPM/FPKMS values for experiments in Expression Atlas.
The app configure.xml needs to be configured as per the experiment being loaded.
Only one type of value per experiment can be loaded in one run.

File locations need to be entered as per the experiment.
StrainOntIds that match with the samples in the experiment are entered after confirming with curators.
Study is created in database with information from curators and study id is entered in the xml file for the experiment.
No of runs in the experiment is specified in the xml file.
Header format has delimiter in the header val of tpms/fpkms values file.
Header val is the values in the header of tpms/fpkms file that matches with the header of experiment-design file. 
Map key is the assembly of the species in the experiment confirmed with curators.
expression Unit is the type of file being loaded. TPM for tpms values and FPKM for fpkms values.


