package com.suhrud.docsy

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.suhrud.docsy.ui.DocsyViewModel
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private val viewModel: DocsyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        updateSecureFlag()

        lifecycleScope.launch {
            viewModel.isAppLocked.collect {
                updateSecureFlag()
            }
        }

        setContent {
            DocsyApp(viewModel = viewModel)
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onAppResume()
        updateSecureFlag()
    }

    override fun onStop() {
        super.onStop()
        // Prevent false relock on screen rotation or theme configuration changes
        if (!isChangingConfigurations) {
            viewModel.onAppPause()
        }
    }

    private fun updateSecureFlag() {
        if (viewModel.appLockManager.isAppLockEnabled()) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
