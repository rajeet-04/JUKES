package androidx.activity.compose

import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.*
import kotlinx.coroutines.*

class ManagedActivityResultLauncher<I, O>(private val contract: ActivityResultContract<I, O>, private val scope: CoroutineScope, private val cb: () -> (O) -> Unit) {
    fun launch(input: I) { scope.launch { val out = withContext(Dispatchers.IO) { contract.pick(input) }; cb()(out) } }
}

@Composable
fun <I, O> rememberLauncherForActivityResult(contract: ActivityResultContract<I, O>, onResult: (O) -> Unit): ManagedActivityResultLauncher<I, O> {
    val scope = rememberCoroutineScope(); val cb = rememberUpdatedState(onResult)
    return remember(contract) { ManagedActivityResultLauncher(contract, scope) { cb.value } }
}
