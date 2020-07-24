package net.pedroloureiro.mvflow

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScope

/**
 * This class exists for reducing the duplication between tests by doing the repeated patterns between them
 */
internal open class MVFlowCounterTestTemplate(
    testCoroutineScope: TestCoroutineScope,
    viewActions: Flow<MVFlowCounterHelper.Action>,
    internal val externalActions: Flow<MVFlowCounterHelper.Action>? = null,
    delayMutations: Boolean = true
) {
    val mvflow: MVFlow<MVFlowCounterHelper.State, MVFlowCounterHelper.Action, MVFlowCounterHelper.Mutation>
    val viewFake: ViewFake<MVFlowCounterHelper.State, MVFlowCounterHelper.Action>
    internal val viewScope = CoroutineScope(testCoroutineScope.coroutineContext + Job())

    init {
        val pair = MVFlowCounterHelper.createFlowAndView(
            viewActions, testCoroutineScope, delayMutations
        )
        mvflow = pair.first
        viewFake = pair.second
    }
}

internal suspend fun TestCoroutineScope.runTestTemplate(
    mvFlowCounterTestTemplate: MVFlowCounterTestTemplate,
    testBlock: suspend MVFlowCounterTestTemplate.() -> Unit
) {
    val viewScope = CoroutineScope(coroutineContext + Job())
    viewScope.launch {
        mvFlowCounterTestTemplate.mvflow.takeView(this, mvFlowCounterTestTemplate.viewFake.view)
    }
    mvFlowCounterTestTemplate.externalActions?.let { mvFlowCounterTestTemplate.mvflow.addExternalActions(it) }
    testBlock.invoke(mvFlowCounterTestTemplate)
    advanceUntilIdle()
    viewScope.cancel()
}
