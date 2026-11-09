package app.luxion.fitarchiveapp.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EanValidatorTest {
    private fun assertValid(expected: String, input: String) {
        assertEquals(EanValidation.Valid(expected), EanValidator.validate(input))
    }

    @Test fun validEan13() { assertValid("3017620422003", "3017620422003") }
    @Test fun validEan8() { assertValid("96385074", "96385074") }
    @Test fun validUpcA() { assertValid("036000291452", "036000291452") }
    @Test fun normalizesSpacesAndDashes() { assertValid("3017620422003", " 3017 6204-22003 ") }

    @Test fun emptyIsInvalid() { assertIs<EanValidation.Invalid>(EanValidator.validate("  ")) }
    @Test fun lettersAreInvalid() { assertIs<EanValidation.Invalid>(EanValidator.validate("30176204220ab")) }
    @Test fun wrongLengthIsInvalid() { assertIs<EanValidation.Invalid>(EanValidator.validate("12345")) }
    @Test fun badCheckDigitIsInvalid() { assertIs<EanValidation.Invalid>(EanValidator.validate("3017620422004")) }
}
