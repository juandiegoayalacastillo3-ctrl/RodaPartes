package com.rodapartes.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.rodapartes.app.R
import com.rodapartes.app.data.AuthRepository
import com.rodapartes.app.data.Fire
import com.rodapartes.app.data.UserRepository
import com.rodapartes.app.data.UserStatus
import com.rodapartes.app.databinding.ActivitySplashBinding
import com.rodapartes.app.util.Router
import com.rodapartes.app.util.Session
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pantalla de bienvenida.
 * Mientras se anima el logo revisa si ya hay una sesión de Firebase guardada:
 * - Si la hay, carga el perfil desde Firestore y abre la interfaz de su rol.
 * - Si no, abre el login.
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivLogo.startAnimation(AnimationUtils.loadAnimation(this, R.anim.logo_spin_in))
        binding.llWordmark.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_up))

        lifecycleScope.launch {
            // La animación y la consulta del perfil corren en paralelo
            val profile = async {
                if (Fire.auth.currentUser == null) null
                else runCatching { UserRepository.current() }.getOrNull()
            }
            delay(1500)
            val user = profile.await()
            if (user != null && user.statusEnum != UserStatus.SUSPENDIDO && user.statusEnum != UserStatus.ELIMINADO) {
                Session.user = user
                Router.goHome(this@SplashActivity, user.roleEnum)
            } else {
                if (user != null) AuthRepository.logout()
                startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                finish()
            }
        }
    }
}
