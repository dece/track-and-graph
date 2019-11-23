package com.samco.trackandgraph.displaytrackgroup

import android.app.Activity
import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getColor
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.*
import androidx.room.withTransaction
import com.samco.trackandgraph.R
import com.samco.trackandgraph.database.TrackAndGraphDatabase
import com.samco.trackandgraph.util.CSVReadWriter
import com.samco.trackandgraph.util.ImportExportFeatureUtils
import kotlinx.coroutines.*
import timber.log.Timber

const val OPEN_FILE_REQUEST_CODE = 124

enum class ImportState { WAITING, IMPORTING, DONE }
class ImportFeaturesDialog : DialogFragment() {
    private var trackGroupName: String? = null
    private var trackGroupId: Long? = null

    private lateinit var viewModel: ImportFeaturesViewModel
    private lateinit var alertDialog: AlertDialog
    private lateinit var fileButton: Button
    private lateinit var progressBar: ProgressBar

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            viewModel = ViewModelProviders.of(this).get(ImportFeaturesViewModel::class.java)
            val view = it.layoutInflater.inflate(R.layout.import_features_dialog, null)
            trackGroupName = arguments!!.getString(TRACK_GROUP_NAME_KEY)
            trackGroupId = arguments!!.getLong(TRACK_GROUP_ID_KEY)

            fileButton = view.findViewById(R.id.fileButton)
            progressBar = view.findViewById(R.id.progressBar)

            progressBar.visibility = View.INVISIBLE
            fileButton.setOnClickListener { onFileButtonClicked() }
            fileButton.text = getString(R.string.select_file)
            fileButton.setTextColor(ContextCompat.getColor(context!!, R.color.errorText))

            val builder = AlertDialog.Builder(it)
            builder.setView(view)
                .setPositiveButton(R.string.importButton) { _, _ -> null }
                .setNegativeButton(R.string.cancel) { _, _ -> null }
            alertDialog = builder.create()
            alertDialog.setCanceledOnTouchOutside(true)
            alertDialog.setOnShowListener { setAlertDialogShowListeners() }
            alertDialog
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    private fun setAlertDialogShowListeners() {
        val positiveButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
        positiveButton.setTextColor(getColor(context!!, R.color.secondaryColor))
        positiveButton.isEnabled = false
        positiveButton.setOnClickListener { onImportClicked() }
        listenToUri()
        listenToImportState()
        listenToException()
        val negativeButton = alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        negativeButton.setTextColor(getColor(context!!, R.color.toolBarTextColor))
        negativeButton.setOnClickListener { dismiss() }
        alertDialog.setOnCancelListener { null }
    }

    private fun listenToUri() {
        viewModel.selectedFileUri.observe(this, Observer { uri ->
            if (uri != null) {
                ImportExportFeatureUtils.setFileButtonTextFromUri(activity, context!!, uri, fileButton, alertDialog)
            }
        })
    }

    private fun listenToImportState() {
        viewModel.importState.observe(this, Observer{ state ->
            when (state) {
                ImportState.WAITING -> {
                    progressBar.visibility = View.INVISIBLE
                }
                ImportState.IMPORTING -> {
                    progressBar.visibility = View.VISIBLE
                    val positiveButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    positiveButton.isEnabled = false
                    fileButton.isEnabled = false
                }
                ImportState.DONE -> dismiss()
            }
        })
    }

    private fun listenToException() {
        viewModel.importException.observe(this, Observer { exception ->
            if (exception != null) {
                val message =
                    if (exception.stringArgs == null) getString(exception.stringId)
                    else getString(exception.stringId, exception.stringArgs)
                Toast.makeText(activity!!, message, Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun onFileButtonClicked() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "text/csv",
                "application/csv",
                "application/comma-separated-values",
                "text/comma-separated-values"
            ))
        }
        startActivityForResult(intent, OPEN_FILE_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == OPEN_FILE_REQUEST_CODE) {
            resultData?.data.also { uri ->
                if (uri != null) {
                    viewModel.selectedFileUri.value = uri
                }
            }
        }
    }

    private fun onImportClicked() {
        progressBar.visibility = View.VISIBLE
        viewModel.beginImport(activity!!, trackGroupId!!)
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        if (viewModel.importState.value != ImportState.IMPORTING) dismiss()
    }
}

class ImportFeaturesViewModel : ViewModel() {
    private var updateJob = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + updateJob)

    val selectedFileUri: MutableLiveData<Uri?> by lazy {
        val uri = MutableLiveData<Uri?>()
        uri.value = null
        return@lazy uri
    }

    val importState: LiveData<ImportState> get() { return _importState }
    private val _importState: MutableLiveData<ImportState> by lazy {
        val state = MutableLiveData<ImportState>()
        state.value = ImportState.WAITING
        return@lazy state
    }

    val importException: LiveData<CSVReadWriter.ImportFeaturesException?> get() { return _importException }
    private val _importException: MutableLiveData<CSVReadWriter.ImportFeaturesException?> by lazy {
        val exception = MutableLiveData<CSVReadWriter.ImportFeaturesException?>()
        exception.value = null
        return@lazy exception
    }

    fun beginImport(activity: Activity, trackGroupId: Long) {
        if (_importState.value == ImportState.IMPORTING) return
        selectedFileUri.value?.let {
            _importState.value = ImportState.IMPORTING
            uiScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val inputStream = activity.contentResolver.openInputStream(it)
                        if (inputStream != null) {
                            val application = activity.application
                            val database = TrackAndGraphDatabase.getInstance(application)
                            val dao = database.trackAndGraphDatabaseDao
                            database.withTransaction {
                                CSVReadWriter.readFeaturesFromCSV(dao, inputStream, trackGroupId)
                            }
                        }
                    }
                } catch (e: CSVReadWriter.ImportFeaturesException) { _importException.value = e }
                _importState.value = ImportState.DONE
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        updateJob.cancel()
    }
}
