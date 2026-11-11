package com.svara.app.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.svara.app.domain.model.FolderRuleType
import com.svara.app.settings.SettingsViewModel

/** Opens the system folder picker and adds the chosen folder to the library. */
@Composable
fun ChooseMusicFolderButton(modifier: Modifier = Modifier, viewModel: SettingsViewModel = hiltViewModel()) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.addFolder(uri.toString(), FolderRuleType.INCLUDE)
    }
    Button(onClick = { picker.launch(null) }, modifier = modifier.heightIn(min = 48.dp)) {
        Icon(Icons.Rounded.CreateNewFolder, null)
        Text("Choose music folder", Modifier.padding(start = 8.dp))
    }
}
