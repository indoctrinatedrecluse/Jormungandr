package org.jormungandr.jupyter.variable

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jormungandr.jupyter.kernel.KernelSession
import org.jormungandr.jupyter.model.CellOutput

@Service(Service.Level.PROJECT)
class VariableInspectorService(private val project: Project) {

    private val gson = Gson()
    private val _variables = MutableStateFlow<List<VariableInfo>>(emptyList())
    val variables: StateFlow<List<VariableInfo>> = _variables.asStateFlow()

    private var activeSession: KernelSession? = null

    companion object {
        fun getInstance(project: Project): VariableInspectorService {
            return project.getService(VariableInspectorService::class.java)
        }
    }

    suspend fun refresh(session: KernelSession) {
        this.activeSession = session
        val introspectCode = """
            import sys, json
            __jg_vars = []
            for __k, __v in list(globals().items()):
                if __k.startswith('_') or __k in ('In', 'Out', 'get_ipython', 'exit', 'quit', 'sys', 'json', '__jg_vars'):
                    continue
                try:
                    __t = type(__v).__name__
                    if __t in ('module', 'function', 'type', 'builtin_function_or_method'):
                        continue
                    __shape = str(getattr(__v, 'shape', '-'))
                    __size = int(getattr(__v, 'nbytes', getattr(__v, '__sizeof__', lambda: 0)()))
                    __is_df = __t in ('DataFrame', 'Series') or hasattr(__v, 'to_csv')
                    __is_polars = 'polars' in str(getattr(type(__v), '__module__', ''))
                    __is_tensor = False
                    __tensor_device = None
                    __tensor_dtype = None
                    if hasattr(__v, 'device') and hasattr(__v, 'dtype'):
                        __is_tensor = True
                        __tensor_device = str(__v.device)
                        __tensor_dtype = str(__v.dtype).replace('torch.', '')
                        if hasattr(__v, 'element_size') and hasattr(__v, 'nelement'):
                            __size = int(__v.element_size() * __v.nelement())
                    __rep = repr(__v)
                    if len(__rep) > 100:
                        __rep = __rep[:97] + '...'
                    __jg_vars.append({
                        'name': __k,
                        'type': __t,
                        'shape': __shape,
                        'size': __size,
                        'preview': __rep,
                        'is_df': __is_df,
                        'is_tensor': __is_tensor,
                        'tensor_device': __tensor_device,
                        'tensor_dtype': __tensor_dtype,
                        'is_polars': __is_polars
                    })
                except Exception:
                    pass
            print('__JG_VARS_JSON__' + json.dumps(__jg_vars))
        """.trimIndent()

        var jsonOutput: String? = null
        session.execute(introspectCode) { output ->
            if (output is CellOutput.StreamOutput) {
                for (line in output.text.lines()) {
                    if (line.startsWith("__JG_VARS_JSON__")) {
                        jsonOutput = line.removePrefix("__JG_VARS_JSON__").trim()
                    }
                }
            }
        }

        if (jsonOutput != null) {
            runCatching {
                val listType = object : TypeToken<List<Map<String, Any>>>() {}.type
                val rawList: List<Map<String, Any>> = gson.fromJson(jsonOutput, listType)
                val parsed = rawList.map { item ->
                    val name = item["name"]?.toString() ?: ""
                    val typeName = item["type"]?.toString() ?: ""
                    val shape = item["shape"]?.toString() ?: "-"
                    val sizeBytes = (item["size"] as? Number)?.toLong() ?: 0L
                    val preview = item["preview"]?.toString() ?: ""
                    val isDf = item["is_df"] as? Boolean ?: false
                    val isTensor = item["is_tensor"] as? Boolean ?: false
                    val tensorDevice = item["tensor_device"]?.toString()
                    val tensorDtype = item["tensor_dtype"]?.toString()
                    val isPolars = item["is_polars"] as? Boolean ?: false
                    VariableInfo(
                        name = name,
                        typeName = typeName,
                        shape = shape,
                        sizeBytes = sizeBytes,
                        sizeFormatted = VariableInfo.formatBytes(sizeBytes),
                        preview = preview,
                        isDataFrame = isDf,
                        isTensor = isTensor,
                        tensorDevice = tensorDevice,
                        tensorDtype = tensorDtype,
                        isPolars = isPolars
                    )
                }
                _variables.value = parsed
            }
        }
    }

    suspend fun exportVariableToCsv(session: KernelSession, varName: String): String? {
        val code = """
            try:
                if hasattr($varName, 'to_csv'):
                    print('__JG_CSV_START__\n' + $varName.to_csv(index=False) + '\n__JG_CSV_END__')
                else:
                    import pandas as pd
                    print('__JG_CSV_START__\n' + pd.DataFrame($varName).to_csv(index=False) + '\n__JG_CSV_END__')
            except Exception as e:
                print('__JG_CSV_ERR__' + str(e))
        """.trimIndent()

        val captured = StringBuilder()
        var insideCsv = false
        session.execute(code) { output ->
            if (output is CellOutput.StreamOutput) {
                for (line in output.text.lines()) {
                    if (line.contains("__JG_CSV_START__")) {
                        insideCsv = true
                    } else if (line.contains("__JG_CSV_END__")) {
                        insideCsv = false
                    } else if (insideCsv) {
                        captured.append(line).append("\n")
                    }
                }
            }
        }

        val res = captured.toString().trim()
        return if (res.isNotEmpty()) res else null
    }

    suspend fun plotVariable(session: KernelSession, varName: String): java.awt.image.BufferedImage? {
        val code = """
            import io, base64
            try:
                import matplotlib
                matplotlib.use('Agg')
                import matplotlib.pyplot as plt
                plt.figure(figsize=(6, 4))
                __v = globals().get('$varName')
                if hasattr(__v, 'plot'):
                    __v.plot()
                elif hasattr(__v, '__len__') and not isinstance(__v, (str, dict)):
                    plt.plot(__v)
                else:
                    plt.plot([__v])
                plt.title('Variable: $varName')
                plt.grid(True, linestyle='--', alpha=0.5)
                __buf = io.BytesIO()
                plt.savefig(__buf, format='png', bbox_inches='tight', dpi=100)
                plt.close('all')
                print('__JG_PLOT_START__\n' + base64.b64encode(__buf.getvalue()).decode('ascii') + '\n__JG_PLOT_END__')
            except Exception as e:
                print('__JG_PLOT_ERR__' + str(e))
        """.trimIndent()

        val captured = StringBuilder()
        var insidePlot = false
        session.execute(code) { output ->
            if (output is CellOutput.StreamOutput) {
                for (line in output.text.lines()) {
                    if (line.contains("__JG_PLOT_START__")) {
                        insidePlot = true
                    } else if (line.contains("__JG_PLOT_END__")) {
                        insidePlot = false
                    } else if (insidePlot) {
                        captured.append(line.trim())
                    }
                }
            }
        }

        val b64 = captured.toString().trim()
        if (b64.isNotEmpty()) {
            return runCatching {
                val bytes = java.util.Base64.getDecoder().decode(b64)
                javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(bytes))
            }.getOrNull()
        }
        return null
    }

    suspend fun openVariableInDataFrameViewer(session: KernelSession, varName: String) {
        val triggerCode = """
            try:
                import jormungandr
                jormungandr.show_dataframe(globals().get('$varName'), title='Variable: $varName')
            except Exception as e:
                print('Error launching DataFrame Viewer:', e)
        """.trimIndent()
        session.execute(triggerCode) {}
    }

    suspend fun inspectTensorDetails(session: KernelSession, varName: String): String? {
        val inspectCode = """
            try:
                import json
                __t = globals().get('$varName')
                __res = {
                    'name': '$varName',
                    'type': str(type(__t).__name__),
                    'shape': str(list(__t.shape) if hasattr(__t, 'shape') else '-'),
                    'dtype': str(getattr(__t, 'dtype', '-')),
                    'device': str(getattr(__t, 'device', 'cpu')),
                    'requires_grad': bool(getattr(__t, 'requires_grad', False)),
                    'numel': int(getattr(__t, 'numel', lambda: 0)()),
                    'min': float(__t.min().item()) if hasattr(__t, 'min') else None,
                    'max': float(__t.max().item()) if hasattr(__t, 'max') else None,
                    'mean': float(__t.float().mean().item()) if hasattr(__t, 'mean') else None
                }
                print('__JG_TENSOR_JSON__' + json.dumps(__res))
            except Exception as e:
                print('__JG_TENSOR_ERR__' + str(e))
        """.trimIndent()
        var jsonResult: String? = null
        session.execute(inspectCode) { out ->
            if (out is CellOutput.StreamOutput) {
                for (l in out.text.lines()) {
                    if (l.startsWith("__JG_TENSOR_JSON__")) {
                        jsonResult = l.removePrefix("__JG_TENSOR_JSON__").trim()
                    }
                }
            }
        }
        return jsonResult
    }

    suspend fun deleteVariable(session: KernelSession, varName: String) {
        session.execute("try:\n    del globals()['$varName']\nexcept Exception:\n    pass") {}
        refresh(session)
    }

    fun getActiveSession(): KernelSession? = activeSession
}
