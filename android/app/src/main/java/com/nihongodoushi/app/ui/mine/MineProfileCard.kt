package com.nihongodoushi.app.ui.mine

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nihongodoushi.app.storage.NicknameCheck
import com.nihongodoushi.app.storage.ProfileRules
import com.nihongodoushi.app.ui.AppState
import com.nihongodoushi.app.ui.theme.paperColors

/**
 * 我的页资料卡（设计定稿 §6.2）：首字圆标 + 昵称 + 副行 + 「编辑」。
 * 编辑对话框三态：缺省预填 / 编辑实时反映 / 校验失败错误近字段（不关、不清、原值不变）。
 */
@Composable
fun MineProfileCard(app: AppState) {
    val paper = paperColors()
    var editing by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = app.profile.nickname.first().toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.profile.nickname,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "本地资料，仅存本机",
                    style = MaterialTheme.typography.labelSmall,
                    color = paper.muted,
                )
            }
            TextButton(
                onClick = { editing = true },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Create,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "编辑", style = MaterialTheme.typography.labelMedium)
            }
        }
    }

    if (editing) {
        EditNicknameDialog(
            current = app.profile.nickname,
            onDismiss = { editing = false },
            onSave = { value ->
                if (app.updateNickname(value)) {
                    editing = false
                }
            },
        )
    }
}

@Composable
private fun EditNicknameDialog(
    current: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(current) }
    var errorReason by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑昵称") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("昵称") },
                    placeholder = { Text(ProfileRules.DEFAULT_NICKNAME) },
                    isError = errorReason != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (errorReason != null) {
                    Text(
                        text = errorReason!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = paperColors().textRed,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.padding(start = 2.dp, top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when (val checked = ProfileRules.check(text)) {
                        is NicknameCheck.Ok -> onSave(checked.value)
                        is NicknameCheck.Invalid -> errorReason = checked.reason
                    }
                },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            ) { Text("取消") }
        },
    )
}
