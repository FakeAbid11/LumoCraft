package com.lumocraft.app.ui.input

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToPx
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lumocraft.app.LumoCraftApplication
import com.lumocraft.app.R
import com.lumocraft.app.domain.input.ButtonLayout
import com.lumocraft.app.domain.input.ControlButton
import com.lumocraft.app.domain.input.ControlKind
import com.lumocraft.app.domain.input.InputManager
import com.lumocraft.app.domain.input.LayoutGeometry
import com.lumocraft.app.domain.input.defaultButtonLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** State of the layout editor. */
data class LayoutEditorUiState(
    val layout: ButtonLayout = defaultButtonLayout(),
    val selectedId: String? = null,
    val dirty: Boolean = false
)

/**
 * Full control layout editor: drag to move, corner handle to resize,
 * opacity + size sliders, snap-to-grid, reset and save. The draft is
 * previewed live through [InputOverlay] with the pipeline paused.
 */
@Composable
fun LayoutEditorScreen(
    onDone: () -> Unit,
    viewModel: LayoutEditorViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = LayoutEditorViewModel.Factory
    ),
) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.input_editor_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(
                        if (state.dirty) R.string.input_editor_unsaved
                        else R.string.input_editor_saved
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = viewModel::reset) {
                    Text(stringResource(R.string.input_editor_reset))
                }
                OutlinedButton(onClick = { viewModel.save(); onDone() }) {
                    Text(stringResource(R.string.input_editor_done))
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(16.dp))
        ) {
            GameSurfaceBackdrop(modifier = Modifier.fillMaxSize())
            InputOverlay(
                manager = viewModel.manager,
                modifier = Modifier.fillMaxSize(),
                interactive = false,
                layoutOverride = state.layout,
                showCursor = false
            )
            EditorEditLayer(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize()
            )
        }

        EditorBottomPanel(
            state = state,
            viewModel = viewModel,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun EditorEditLayer(
    state: LayoutEditorUiState,
    viewModel: LayoutEditorViewModel,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val surfaceWidth = maxWidth
        val surfaceHeight = maxHeight
        val density = LocalDensity.current

        state.layout.buttons.forEach { button ->
            val selected = button.id == state.selectedId
            val widthDp = surfaceWidth * button.width
            val heightDp = surfaceHeight * button.height
            val centerX = surfaceWidth * button.x
            val centerY = surfaceHeight * button.y

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (centerX - widthDp / 2f).roundToPx(),
                            (centerY - heightDp / 2f).roundToPx()
                        )
                    }
                    .size(widthDp, heightDp)
                    .pointerInput(button.id) {
                        detectTapGestures { viewModel.select(button.id) }
                    }
                    .pointerInput(button.id) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val widthPx = with(density) { surfaceWidth.toPx() }
                            val heightPx = with(density) { surfaceHeight.toPx() }
                            viewModel.moveButton(button.id, dragAmount.x / widthPx, dragAmount.y / heightPx)
                        }
                    }
                    .then(
                        if (selected) {
                            Modifier.border(2.dp, Color(0xFF64D2FF), CircleShape)
                        } else {
                            Modifier
                        }
                    )
            ) {
                if (button.kind == ControlKind.BUTTON) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .size(18.dp)
                            .background(Color(0xCC64D2FF), CircleShape)
                            .pointerInput(button.id) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val widthPx = with(density) { surfaceWidth.toPx() }
                                    val heightPx = with(density) { surfaceHeight.toPx() }
                                    val factor = 1f + (dragAmount.x / widthPx + dragAmount.y / heightPx) * 2f
                                    viewModel.resizeButton(button.id, factor)
                                }
                            }
                    )
                }
                Text(
                    text = button.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}

@Composable
private fun EditorBottomPanel(
    state: LayoutEditorUiState,
    viewModel: LayoutEditorViewModel,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.padding(12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val selected = state.selectedId?.let { id -> state.layout.find(id) }
            if (selected == null) {
                Text(
                    text = stringResource(R.string.input_editor_select_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = selected.label,
                    style = MaterialTheme.typography.titleSmall
                )
                SliderRow(
                    label = stringResource(R.string.input_editor_size),
                    value = selected.width,
                    valueRange = 0.07f..0.45f,
                    onValueChange = { viewModel.setSize(selected.id, it) }
                )
                SliderRow(
                    label = stringResource(R.string.input_editor_opacity),
                    value = selected.opacity,
                    valueRange = 0.1f..1f,
                    onValueChange = { viewModel.setOpacity(selected.id, it) }
                )
            }
            Text(
                text = stringResource(R.string.input_editor_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Holds the edited draft until save; geometry math goes through
 * [LayoutGeometry] so snapping and clamping live outside Compose.
 */
class LayoutEditorViewModel(
    val manager: InputManager,
) : ViewModel() {

    private val _layout = MutableStateFlow(manager.activeProfile.value.buttonLayout)
    private val _selectedId = MutableStateFlow<String?>(null)
    private val _dirty = MutableStateFlow(false)

    val uiState: StateFlow<LayoutEditorUiState> = combine(
        _layout, _selectedId, _dirty
    ) { layout, selectedId, dirty ->
        LayoutEditorUiState(layout, selectedId, dirty)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LayoutEditorUiState(_layout.value, _selectedId.value, _dirty.value)
    )

    fun select(buttonId: String) {
        _selectedId.value = buttonId
    }

    fun moveButton(buttonId: String, dxNorm: Float, dyNorm: Float) {
        updateButton(buttonId) { button ->
            val halfW = button.width / 2f
            val halfH = button.height / 2f
            button.copy(
                x = LayoutGeometry.snapOrFree(LayoutGeometry.clamp(button.x + dxNorm, halfW, 1f - halfW)),
                y = LayoutGeometry.snapOrFree(LayoutGeometry.clamp(button.y + dyNorm, halfH, 1f - halfH))
            )
        }
    }

    fun resizeButton(buttonId: String, factor: Float) {
        updateButton(buttonId) { button ->
            val width = LayoutGeometry.clamp(button.width * factor, MIN_SIZE, MAX_SIZE)
            button.copy(width = width, height = button.height * (width / button.width))
        }
    }

    fun setSize(buttonId: String, fraction: Float) {
        updateButton(buttonId) { button ->
            val width = LayoutGeometry.clamp(fraction, MIN_SIZE, MAX_SIZE)
            button.copy(width = width, height = button.height * (width / button.width))
        }
    }

    fun setOpacity(buttonId: String, opacity: Float) {
        updateButton(buttonId) { it.copy(opacity = LayoutGeometry.clamp(opacity, 0.1f, 1f)) }
    }

    fun reset() {
        _layout.value = defaultButtonLayout()
        _selectedId.value = null
        _dirty.value = true
    }

    fun save() {
        manager.setLayout(_layout.value)
        _dirty.value = false
    }

    private fun updateButton(buttonId: String, transform: (ControlButton) -> ControlButton) {
        _layout.value = _layout.value.copy(
            buttons = _layout.value.buttons.map { if (it.id == buttonId) transform(it) else it }
        )
        _dirty.value = true
    }

    companion object {
        const val MIN_SIZE = 0.07f
        const val MAX_SIZE = 0.45f

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as LumoCraftApplication
                LayoutEditorViewModel(application.inputManager)
            }
        }
    }
}