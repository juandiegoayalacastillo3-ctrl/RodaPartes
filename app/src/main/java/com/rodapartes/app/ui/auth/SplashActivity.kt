package com.rodapartes.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.rodapartes.app.R
import com.rodapartes.app.data.Role
import com.rodapartes.app.databinding.ActivitySplashBinding
import com.rodapartes.app.util.Router
import com.rodapartes.app.util.Session
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Pantalla de bienvenida: anima el logo y decide a dónde ir. */
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Animación definida en res/anim (giro del anillo como una rueda)
        binding.ivLogo.startAnimation(AnimationUtils.loadAnimation(this, R.anim.logo_spin_in))
        binding.llWordmark.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_up))

        // Corrutina: espera sin bloquear el hilo principal y luego navega
        lifecycleScope.launch {
            delay(1800)
            val session = Session(this@SplashActivity)
            if (session.loggedIn) {
                Router.goHome(this@SplashActivity, session.role ?: Role.BUYER)
            } else {
                startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                finish()
            }
        }
    }
}
