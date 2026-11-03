package com.boratreino.core.health

/** Where Health Connect stands on this phone. */
sealed interface HealthAvailability {
    /** No Health Connect on this device at all: the switch is hidden. */
    data object Unsupported : HealthAvailability

    /** Health Connect can be installed from the store. */
    data object NotInstalled : HealthAvailability

    /** Health Connect is installed but too old. */
    data object NeedsUpdate : HealthAvailability

    /** Available, but not all write permissions are granted. */
    data object NeedsPermission : HealthAvailability

    data object Ready : HealthAvailability
}
