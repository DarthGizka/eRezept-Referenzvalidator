package de.abda.fhir.validator.core;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import de.abda.fhir.validator.core.util.FileHelper;
import de.abda.fhir.validator.core.util.Profile;
import de.abda.fhir.validator.core.util.ProfileHelper;
import de.abda.fhir.validator.core.util.ProfileValidityDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

/**
 * This is the default class to use the ABDA FHIR validator in your Java Application.
 * To validate a File, you can use the {@link #validateFile(Path)}, if the file content is
 * already read as String, you can use {@link #validateString(String)}. The profile data is loaded
 * on demand the first time a profile version is used. Further invocations use the already
 * loaded data.
 *
 * <p>ReferenceValidator is currently NOT threadsafe, but it can be reused for validating
 * further FHIR resources in the same or another thread.</p>
 */
public class ReferenceValidator {
    static Logger logger = LoggerFactory.getLogger(Validator.class);
    private FhirContext ctx ;
    private ValidatorHolder validatorHolder;

    /**
     * Creates a new instance without parameters.
     */
    public ReferenceValidator() {
        this(FhirContext.forR4());
    }

    /**
     * Creates a new instance using an existing FhirContext.
     * @param ctx {@link FhirContext}, not null
     */
    public ReferenceValidator(FhirContext ctx) {
        this.ctx = ctx;
        validatorHolder = new ValidatorHolder(ctx);
    }
    /**
     * Validates the given File
     * @param inputFile Path, not null
     * @param noInstanceValidityCheck, boolean
     * @param profileValidateAgainst, List<String>
     * @return Map of {@link ResultSeverityEnum} as key and a List of {@link SingleValidationMessage} as key
     */
    public Map<ResultSeverityEnum, List<SingleValidationMessage>> validateFile(String inputFile, boolean noInstanceValidityCheck, List<String> profileValidateAgainst) {
        logger.debug("Start validating File: {}", inputFile);
        String validatorInputAsString = FileHelper.loadValidatorInputAsString(inputFile);
        return this.validateImpl(validatorInputAsString, noInstanceValidityCheck, profileValidateAgainst);
    }

    public Map<ResultSeverityEnum, List<SingleValidationMessage>> validateFile(String inputFile) {
        return this.validateFile(inputFile, false, null);
    }
    /**
     * Validates the given File
     * @param inputFile String path, not null or empty
     * @param noInstanceValidityCheck, boolean
     * @param profileValidateAgainst, List<String>
     * @return Map of {@link ResultSeverityEnum} as key and a List of {@link SingleValidationMessage} as key
     */
    public Map<ResultSeverityEnum, List<SingleValidationMessage>> validateFile(Path inputFile, boolean noInstanceValidityCheck, List<String> profileValidateAgainst) {
        return validateFile(inputFile.toString(), noInstanceValidityCheck, profileValidateAgainst);
    }

    public Map<ResultSeverityEnum, List<SingleValidationMessage>> validateFile(Path inputFile) {
        return validateFile(inputFile.toString(), false, null);
    }
    /**
     * Validates the given String containing a FHIR resource
     * @param validatorInputAsString String, not null or empty
     * @param noInstanceValidityCheck, boolean
     * @param profileValidateAgainst, List<String>
     * @return Map of {@link ResultSeverityEnum} as key and a List of {@link SingleValidationMessage} as key
     */
    public Map<ResultSeverityEnum, List<SingleValidationMessage>> validateString(String validatorInputAsString, boolean noInstanceValidityCheck, List<String> profileValidateAgainst) {
        logger.debug("Start validating String input");
        return validateImpl(validatorInputAsString, noInstanceValidityCheck, profileValidateAgainst);
    }

    /**
     * The first validation in a new validator is very slow. So this method creates validators
     * for all supported profiles and loads all necessary data, so the calls to the validator
     * will be fast afterwards.
     * @param profileToPreload a var ags array of profiles, that will be preloaded. If this is null or
     *                         empty, then all profiles will be preloaded
     */
    public void preloadAllSupportedValidators(ProfileForPreloading... profileToPreload){
        validatorHolder.preloadAllSupportedValidators(profileToPreload);
    }

    private Map<ResultSeverityEnum, List<SingleValidationMessage>> validateImpl(String validatorInputAsString) {
        return validateImpl(validatorInputAsString, false, null);
    }

    private Map<ResultSeverityEnum, List<SingleValidationMessage>> validateImpl(String validatorInputAsString, boolean noInstanceValidityCheck, List<String> profileValidateAgainst) {
        Profile instanceProfile = null;
        ProfileValidityDate instanceProfileValidityDate = null;
        Map<ResultSeverityEnum, List<SingleValidationMessage>> instanceValidityCheckResults = new HashMap<>();
        String tmp_str;

        // TODO: ReleaseVersionsausgabe !?! oder Option Auswertung Instanz? -> log4J ?!?
        logger.info("Validator Version 1.0.0");
        //ValidationMessageAdd(instanceValidityCheckResults, ResultSeverityEnum.INFORMATION, "Validator Version 1.0.0");
        if (noInstanceValidityCheck) {
            logger.warn("noInstanceValidityCheck: " + noInstanceValidityCheck);
            ValidationMessageAdd(instanceValidityCheckResults, ResultSeverityEnum.WARNING, "noInstanceValidityCheck: " + noInstanceValidityCheck);
        } else {
            logger.info("noInstanceValidityCheck: " + noInstanceValidityCheck);
            ValidationMessageAdd(instanceValidityCheckResults, ResultSeverityEnum.INFORMATION, "noInstanceValidityCheck: " + noInstanceValidityCheck);
        }

        InputStream validatorInputStream = new ByteArrayInputStream(validatorInputAsString.getBytes(StandardCharsets.UTF_8));
        if (noInstanceValidityCheck) {
            instanceProfile = ProfileHelper.getProfileFromXmlStream(validatorInputStream);
        } else { // if (!noInstanceValidityCheck) {
            instanceProfileValidityDate = ProfileHelper.getProfileValidityDateFromXmlStream(validatorInputStream, validatorHolder);
            if (instanceProfileValidityDate == null || instanceProfileValidityDate.getValidityPeriod() == null) {
                ValidationMessageAdd(instanceValidityCheckResults, ResultSeverityEnum.FATAL, "validityPeriod for profile not found");
            } else {
                if (instanceProfileValidityDate.getInstanceDate() == null) {
                    logger.debug("instanceDate null");
                    ValidationMessageAdd(instanceValidityCheckResults, ResultSeverityEnum.FATAL, "no Instance date");
                } else {
                    //logger.debug(instanceProfileValidityDate.getInstanceDate().toString());
                    instanceProfile = instanceProfileValidityDate.getProfile();
                    // TODO: Was bei Zeitangaben und Verschiebungen in der Datumsangabe?!? -> Implementierung zur Zeit: Datum in deutscher Zeit!!!
                    // .. für Verordnung, Abgabedaten (GKV/PKV) und Abrechnungsdaten (TA7) = ok
                    if ((instanceProfileValidityDate.getInstanceDate().isAfter(instanceProfileValidityDate.getValidityPeriod().getValid_from()) && instanceProfileValidityDate.getInstanceDate().isBefore(instanceProfileValidityDate.getValidityPeriod().getValid_to())) || instanceProfileValidityDate.getInstanceDate().isEqual(instanceProfileValidityDate.getValidityPeriod().getValid_from()) || instanceProfileValidityDate.getInstanceDate().isEqual(instanceProfileValidityDate.getValidityPeriod().getValid_to())) {
                        tmp_str = "Instance valid: " + instanceProfileValidityDate.getInstanceDate().toString() + " between " + instanceProfileValidityDate.getValidityPeriod().getValid_from().toString() + " and " + instanceProfileValidityDate.getValidityPeriod().getValid_to().toString();
                        logger.info(tmp_str);
                        ValidationMessageAdd(instanceValidityCheckResults, ResultSeverityEnum.INFORMATION, tmp_str);
                    } else {
                        tmp_str = "Instance invalid: " + instanceProfileValidityDate.getInstanceDate().toString() + " not between " + instanceProfileValidityDate.getValidityPeriod().getValid_from().toString() + " and " + instanceProfileValidityDate.getValidityPeriod().getValid_to().toString();
                        logger.error(tmp_str);
                        ValidationMessageAdd(instanceValidityCheckResults, ResultSeverityEnum.ERROR, tmp_str);
                    }
                }
            }
        }
        // TODO: Stream schließen... ?!?
        //validatorInputStream.close();
        //https://stackoverflow.com/questions/309424/how-do-i-read-convert-an-inputstream-into-a-string-in-java

        if (!noInstanceValidityCheck && (instanceValidityCheckResults.getOrDefault(ResultSeverityEnum.ERROR, Collections.emptyList()).size() != 0
                || instanceValidityCheckResults.getOrDefault(ResultSeverityEnum.FATAL, Collections.emptyList()).size() != 0)) {
            return instanceValidityCheckResults;
        } else if ((instanceProfile != null) && (!profileValidateAgainst(profileValidateAgainst, instanceProfile.getBaseCanonical()))) {
            tmp_str = "profile: " + instanceProfile.getBaseCanonical() + " does not match the parameter(s): " + profileValidateAgainst.toString();
            logger.error(tmp_str);
            ValidationMessageAdd(instanceValidityCheckResults, ResultSeverityEnum.ERROR, tmp_str);
            return instanceValidityCheckResults;
        } else if (instanceProfile != null) {
            Validator validator = validatorHolder.getValidatorForProfile(instanceProfile);
            if (validator != null) {
                Map<ResultSeverityEnum, List<SingleValidationMessage>> output = validator.validate(validatorInputAsString);
                output.putAll(instanceValidityCheckResults);
                return output;
            } else {
                ValidationMessageAdd(instanceValidityCheckResults, ResultSeverityEnum.ERROR, "Profile unbekannt!");
                return instanceValidityCheckResults;
            }
        } else {
            ValidationMessageAdd(instanceValidityCheckResults, ResultSeverityEnum.ERROR, "Profile nicht erkannt!");
            return instanceValidityCheckResults;
        }
    }

    private boolean profileValidateAgainst(List<String> profileValidateAgainst, String instanceProfile) {
        boolean retVal = false;
        if ((profileValidateAgainst == null) || (profileValidateAgainst.isEmpty())) {
            retVal = true;
        } else {
            for (int i = 0; i < profileValidateAgainst.size(); i++) {
                if (profileValidateAgainst.get(i).equals(instanceProfile)) {
                    retVal = true;
                    break;
                }
            }
        }
        return retVal;
    }

    private void ValidationMessageAdd(Map<ResultSeverityEnum, List<SingleValidationMessage>> instanceValidityCheckResults, ResultSeverityEnum inResultSeverityEnum, String inMessage) {
        // create new Message
        SingleValidationMessage mySingleValidationMessage = new SingleValidationMessage();
        mySingleValidationMessage.setSeverity(inResultSeverityEnum);
        mySingleValidationMessage.setMessage(inMessage);

        if(instanceValidityCheckResults.containsKey(inResultSeverityEnum)){
            instanceValidityCheckResults.get(inResultSeverityEnum).add(mySingleValidationMessage);
        } else {
            ArrayList<SingleValidationMessage> mySingleValidationMessageList  = new ArrayList<>();
            mySingleValidationMessageList.add(mySingleValidationMessage);
            instanceValidityCheckResults.put(inResultSeverityEnum, mySingleValidationMessageList);
        }
    }
}
