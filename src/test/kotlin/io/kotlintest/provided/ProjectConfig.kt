package io.kotlintest.provided

import io.kotest.core.config.AbstractProjectConfig

class ProjectConfig : AbstractProjectConfig() {
    override val parallelism: Int
        get() = 1
}