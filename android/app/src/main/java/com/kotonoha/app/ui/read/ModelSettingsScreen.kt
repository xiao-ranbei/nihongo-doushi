package com.kotonoha.app.ui.read

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kotonoha.app.engine.read.ModelConfig
import com.kotonoha.app.engine.read.ReadRules
import com.kotonoha.app.net.AnalyzerError
import com.kotonoha.app.net.AnalyzerResult
import com.kotonoha.app.net.HttpAnalyzer
import com.kotonoha.app.ui.AppState
import com.kotonoha.app.ui.common.ModuleTopBar
import com.kotonoha.app.ui.saveReadConfig
import com.kotonoha.app.ui.theme.geometry
import com.kotonoha.app.ui.theme.paperColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 模型 / API 设置页（spec §9.2）。
 *
 * 结构自上而下：页头 → 说明卡 → 表单卡 → 保存区 → 测试连接区 → 隐私告知。
 * 本页目前**没有路由入口**（批 3 接线），但状态全部走 [AppState]，接线时不需要改动这里。
 *
 * 三个刻意的实现选择：
 * 1. 密钥默认显示掩码，聚焦或点「修改」才切到编辑态——避免用户误改已保存的密钥；
 *    编辑态一旦开始就以输入框内容为准，取消编辑会让原密钥重新生效。
 * 2. 测试连接**不复用**阅读页的缓存路径，只调 [HttpAnalyzer.analyze] 然后丢弃结果：
 *    设置页的作用是验证连通性，往缓存里塞一句「テスト」只会污染真实数据。
 * 3. 温度滑杆取值后做小数点收敛（roundToInt/10.0）：Slider 连续拖动会给出 0.30000001
 *    这种值，直接存进配置会在 JSON 里出现一长串数字，也让「0.1 步进」名不副实。
 */
@Composable
fun ModelSettingsScreen(app: AppState, onBack: () -> Unit) {
    val g = geometry()
    val paper = paperColors()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val saved = app.readConfig
    var baseUrl by remember { mutableStateOf(saved.baseUrl) }
    var modelName by remember { mutableStateOf(saved.model) }
    var temperature by remember { mutableStateOf(saved.temperature.toFloat()) }
    // 已保存过密钥时不进入编辑态：先把用户挡在掩码后面，避免他以为输入框里是完整密钥
    var keyEditing by remember { mutableStateOf(saved.apiKey.isBlank()) }
    var keyInput by remember { mutableStateOf("") }
    var keyVisible by remember { mutableStateOf(false) }

    var errorText by remember { mutableStateOf<String?>(null) }
    var testText by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    val effectiveKey = if (keyEditing) keyInput else saved.apiKey
    val ready = baseUrl.isNotBlank() && modelName.isNotBlank() && effectiveKey.isNotBlank()
    val endpointPreview = if (baseUrl.isBlank()) "先填写服务地址" else ReadRules.normalizeEndpoint(baseUrl)

    fun currentConfig(): ModelConfig = ModelConfig(
        baseUrl = baseUrl.trim(),
        model = modelName.trim(),
        apiKey = effectiveKey.trim(),
        temperature = temperature.toDouble(),
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ModuleTopBar(title = "模型/API 设置", onBack = onBack)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingsIntroCard(paper.textStrong, paper.muted)

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(g.cardRadius),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = g.cardShadow,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        FieldLabel("服务地址")
                        OutlinedTextField(
                            value = baseUrl,
                            onValueChange = { baseUrl = it; errorText = null },
                            singleLine = true,
                            placeholder = { Text("https://api.example.com/v1") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "实际请求：" + endpointPreview,
                            style = MaterialTheme.typography.labelSmall,
                            color = paper.muted,
                        )

                        FieldLabel("模型名称")
                        OutlinedTextField(
                            value = modelName,
                            onValueChange = { modelName = it; errorText = null },
                            singleLine = true,
                            placeholder = { Text("按服务商文档填写") },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        FieldLabel("API 密钥")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = if (keyEditing) keyInput else maskKey(saved.apiKey),
                                onValueChange = { if (keyEditing) { keyInput = it; errorText = null } },
                                singleLine = true,
                                readOnly = !keyEditing,
                                placeholder = { Text("粘贴服务商提供的密钥") },
                                visualTransformation = if (keyEditing && keyVisible) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier
                                    .weight(1f)
                                    .onFocusChanged {
                                        // 聚焦即进入编辑态：用户已经明确要改，不必再点一次「修改」
                                        if (it.isFocused && !keyEditing && saved.apiKey.isNotBlank()) {
                                            keyEditing = true
                                            keyInput = ""
                                        }
                                    },
                            )
                            TextButton(
                                onClick = {
                                    if (!keyEditing) {
                                        keyEditing = true
                                        keyInput = ""
                                    } else {
                                        keyVisible = !keyVisible
                                    }
                                },
                            ) {
                                Text(if (!keyEditing) "修改" else if (keyVisible) "隐藏" else "显示")
                            }
                        }

                        FieldLabel("温度 " + formatTemperature(temperature))
                        Slider(
                            value = temperature,
                            onValueChange = { temperature = snapTemperature(it) },
                            valueRange = 0f..1f,
                            steps = 9,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "越低越稳定，阅读解析建议 0.2 左右。",
                            style = MaterialTheme.typography.labelSmall,
                            color = paper.muted,
                        )

                        if (errorText != null) {
                            Text(
                                text = errorText.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = paper.textRed,
                            )
                        }

                        Button(
                            onClick = {
                                val cfg = currentConfig()
                                if (app.saveReadConfig(cfg)) {
                                    errorText = null
                                    keyEditing = false
                                    keyInput = ""
                                    scope.launch { snackbar.showSnackbar("已保存") }
                                } else {
                                    errorText = ReadRules.validateConfig(cfg).firstOrNull() ?: "保存失败"
                                }
                            },
                            enabled = ready,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("保存")
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(g.cardRadius),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = g.cardShadow,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FieldLabel("测试连接")
                        Text(
                            text = "用固定句子「テスト」发一次解析请求，不写入缓存。",
                            style = MaterialTheme.typography.bodySmall,
                            color = paper.textStrong,
                        )
                        OutlinedButton(
                            onClick = {
                                if (testing) return@OutlinedButton
                                testing = true
                                testText = "请求中…"
                                val cfg = currentConfig()
                                scope.launch {
                                    val startedAt = System.nanoTime()
                                    val result = HttpAnalyzer().analyze("テスト", cfg)
                                    val elapsed = (System.nanoTime() - startedAt) / 1_000_000
                                    testText = when (result) {
                                        is AnalyzerResult.Ok -> "成功 · " + elapsed + "ms"
                                        is AnalyzerResult.Err -> analyzerErrorText(result.error)
                                    }
                                    testing = false
                                }
                            },
                            enabled = ready && !testing,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (testing) "测试中…" else "测试连接")
                        }
                        if (testText != null) {
                            Text(
                                text = testText.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (testText.orEmpty().startsWith("成功")) paper.textGreen else paper.textRed,
                            )
                        }
                    }
                }

                PrivacyNotice(paper.muted)
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }
}

@Composable
private fun SettingsIntroCard(strong: androidx.compose.ui.graphics.Color, muted: androidx.compose.ui.graphics.Color) {
    val g = geometry()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(g.cardRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = g.cardShadow,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "配置你自己的模型服务",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "本应用的 AI 能力全部由你提供的 OpenAI 兼容接口完成，" +
                    "任何一家 Chat Completions 兼容服务都可以填。" +
                    "不填就不能使用 AI 解析与查词。",
                style = MaterialTheme.typography.bodyMedium,
                color = strong,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "地址填到服务商给出的 /v1 即可，应用会自动补全 /chat/completions。",
                style = MaterialTheme.typography.labelSmall,
                color = muted,
            )
        }
    }
}

@Composable
private fun PrivacyNotice(muted: androidx.compose.ui.graphics.Color) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Text(
            text = "关于隐私",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = muted,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "文章内容会发送到你填写的服务地址；密钥以明文保存在本机应用私有目录，" +
                "不会上传到本项目任何服务器（本项目没有服务器）。",
            style = MaterialTheme.typography.labelSmall,
            color = muted,
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/** 掩码回显：只留末 4 位，长度不足 4 位时整体隐藏，避免把短密钥直接印出来 */
private fun maskKey(key: String): String {
    if (key.isBlank()) return ""
    val tail = if (key.length > 4) key.takeLast(4) else ""
    return "••••••" + tail
}

/** 拖动滑杆会给出 0.30000001 这类值，收敛到 0.1 步进 */
private fun snapTemperature(value: Float): Float = (value * 10f).roundToInt() / 10f

private fun formatTemperature(value: Float): String {
    val tenth = (value * 10f).roundToInt()
    return (tenth / 10).toString() + "." + (tenth % 10).toString()
}

/**
 * spec §12 的 11 类文案映射。设置页与阅读页共用同一套说法，
 * 所以这里逐类覆盖而不是给一句「请求失败」——用户看到「密钥无效」才知道该改哪一行。
 */
/** 提升为 internal：阅读器页要复用同一套中文文案，错误映射只能有一个出处 */
internal fun analyzerErrorText(error: AnalyzerError): String = when (error) {
    is AnalyzerError.NotConfigured -> "先去配置模型与服务地址"
    is AnalyzerError.Network -> "网络不可用或地址填错了"
    is AnalyzerError.Timeout -> "请求超时（60 秒），这一句可能太长"
    is AnalyzerError.Auth -> "密钥无效或没有该模型的权限"
    is AnalyzerError.Endpoint -> "接口地址不对，检查服务地址"
    is AnalyzerError.RateLimit -> "请求太频繁，稍后再试"
    is AnalyzerError.Server -> "服务端错误（" + error.code + "）"
    is AnalyzerError.BadRequest -> "请求被拒绝（" + error.code + "）：" + error.body
    is AnalyzerError.Malformed -> "模型返回结构异常"
    is AnalyzerError.Truncated -> "返回被截断，句子可能过长"
    is AnalyzerError.ParseFailed -> "模型返回格式异常"
}
