package com.orchords.orchordsai.ui.components.ai

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.orchords.orchordsai.ui.components.ui.ToggleSurface

@Composable
fun PlanningModeButton(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    ToggleSurface(
        modifier = modifier.semantics { role = Role.Switch },
        checked = enabled,
        onClick = { onToggle(!enabled) },
    ) {
        Text(
            text = "Plan",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
