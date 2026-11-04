package com.easyfood.model

/** Resultado de validar un EAN escrito a mano. */
sealed interface EanValidation {
    /** [ean] ya está normalizado (sin espacios ni guiones). */
    data class Valid(val ean: String) : EanValidation
    data class Invalid(val reason: String) : EanValidation
}

/** Valida códigos GTIN (EAN-8, UPC-A de 12, EAN-13 y GTIN-14) con su dígito de control. */
object EanValidator {
    private val VALID_LENGTHS = setOf(8, 12, 13, 14)

    fun validate(input: String): EanValidation {
        val ean = input.filterNot { it.isWhitespace() || it == '-' }
        if (ean.isEmpty()) return EanValidation.Invalid("Introduce un código de barras")
        if (!ean.all { it in '0'..'9' }) return EanValidation.Invalid("Solo se admiten dígitos")
        if (ean.length !in VALID_LENGTHS) {
            return EanValidation.Invalid("Debe tener 8, 12, 13 o 14 dígitos (tiene ${ean.length})")
        }
        if (!hasValidCheckDigit(ean)) return EanValidation.Invalid("El dígito de control no es correcto")
        return EanValidation.Valid(ean)
    }

    private fun hasValidCheckDigit(ean: String): Boolean {
        val digits = ean.map { it - '0' }
        // De derecha a izquierda, sin contar el dígito de control: pesos 3,1,3,1...
        val sum = digits.dropLast(1).reversed().withIndex().sumOf { (i, d) -> if (i % 2 == 0) d * 3 else d }
        return (10 - sum % 10) % 10 == digits.last()
    }
}
