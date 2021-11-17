package de.abda.fhir.validator.core;

import static org.apache.commons.lang3.tuple.Pair.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import de.abda.fhir.validator.core.exception.ValidatorInitializationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Unittest for {@link DynamicValidator}
 *
 * @author RupprechJo
 */
class DynamicValidatorTest {

  private static final Path VALID_BASE_DIR = Paths.get("src/test/resources/valid");
  private static final Path INVALID_BASE_DIR = Paths.get("src/test/resources/invalid");
  private static final Path EXCEPTION_BASE_DIR = Paths.get("src/test/resources/exception");

  static DynamicValidator validator = new DynamicValidator();

  @ParameterizedTest
  @MethodSource
  void validateValidFile(Path path) {
    Map<ResultSeverityEnum, List<SingleValidationMessage>> errors = validator
        .validate(path);
    String mapAsString = errors.keySet().stream()
        .map(key -> key + ": " + errors.get(key).size())
        .collect(Collectors.joining(","));
    System.out.println(mapAsString);
    assertEquals(0, getFatalAndErrorMessages(errors).size());
  }

  private static Stream<Path> validateValidFile() throws IOException {
    return Files.list(VALID_BASE_DIR).filter(path -> path.toString().endsWith(".xml"));
  }

  @ParameterizedTest
  @MethodSource
  void validateInvalidFile(Pair<Path, String> arguments) {
    Map<ResultSeverityEnum, List<SingleValidationMessage>> errors = validator
        .validate(arguments.getKey());
    String mapAsString = errors.keySet().stream()
        .map(key -> key + ": " + errors.get(key).size())
        .collect(Collectors.joining(","));
    System.out.println(mapAsString);
    List<SingleValidationMessage> errorMessages = getFatalAndErrorMessages(errors);
    assertNotEquals(0, errorMessages.size());
    assertTrue(errorMessages.stream()
        .anyMatch(message -> message.getMessage().contains(arguments.getValue())));
  }

  private static Stream<Pair<Path, String>> validateInvalidFile() throws IOException {
    return Stream.of(
        of(INVALID_BASE_DIR.resolve("InvalidEprescriptionBundle1.xml"),
            "Der Wert ist \"https://fhir.kbv.de/CodeSystem/Wrong\", muss aber \"https://fhir.kbv.de/CodeSystem/KBV_CS_ERP_Section_Type\" sein")

    );
  }

  @ParameterizedTest
  @MethodSource
  void validateFileWithException(Pair<Path, Class<? extends Exception>> arguments) {
    Assertions.assertThrows(arguments.getRight(), () -> validator.validate(arguments.getKey()));
  }

  private static Stream<Pair<Path, Class<? extends Exception>>> validateFileWithException()
      throws IOException {
    return Stream.of(
        of(EXCEPTION_BASE_DIR.resolve("NotExistingBundle.xml"),
            ValidatorInitializationException.class),
        of(EXCEPTION_BASE_DIR.resolve("NotExistingBundleVersion.xml"),
            ValidatorInitializationException.class)

    );
  }

  private List<SingleValidationMessage> getFatalAndErrorMessages(
      Map<ResultSeverityEnum, List<SingleValidationMessage>> errors) {
    List<SingleValidationMessage> result = new ArrayList<>(
        errors.getOrDefault(ResultSeverityEnum.ERROR,
            Collections.emptyList()));
    result.addAll(errors.getOrDefault(ResultSeverityEnum.FATAL, Collections.emptyList()));
    return result;
  }

}