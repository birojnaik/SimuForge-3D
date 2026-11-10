package com.example.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.physics.SimulationUiState
import com.example.ui.theme.AmberClothSecondary
import com.example.ui.theme.CyanFluidPrimary
import com.example.ui.theme.StudioBorderSubtle
import com.example.ui.theme.StudioCardElevated
import com.example.ui.theme.StudioDeepNavy
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.TextSecondaryMuted
import com.example.ui.theme.VioletGravityTertiary
import java.util.Locale

@Composable
fun MathInspectorDialog(
    uiState: SimulationUiState,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("dialog_math_inspector"),
            color = StudioDeepNavy,
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CyanFluidPrimary.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_sim_header_banner_1794296058323),
                    contentDescription = stringResource(R.string.cd_banner_preview),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(105.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, StudioBorderSubtle, RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )

                Text(
                    text = "SIMULATION vs. EMULATION: MATHEMATICAL SOLVERS",
                    style = MaterialTheme.typography.titleMedium,
                    color = CyanFluidPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Unlike procedural animation or keyframe emulation, SimuForge 3D integrates continuous partial and ordinary differential equations forward in time (Δt) with conservation of momentum and energy.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondaryMuted
                )

                EquationBlockCard(
                    title = "1. 3D Navier-Stokes SPH Fluid Dynamics",
                    accent = CyanFluidPrimary,
                    equation = "ρ_i = Σ m_j W_poly6(|r_i - r_j|, h)\n" +
                        "f_i^press = -Σ m_j (P_i + P_j)/(2ρ_j) ∇W_spiky\n" +
                        "f_i^visc  = μ Σ m_j (v_j - v_i)/ρ_j ∇²W_visc",
                    liveSubstitution = String.format(
                        Locale.US,
                        "Active h = %.2f m | k = %.2f Pa | μ = %.2f Pa·s",
                        uiState.fluidSmoothingRadius,
                        uiState.fluidGasStiffness,
                        uiState.fluidViscosity
                    )
                )

                EquationBlockCard(
                    title = "2. Verlet Cloth & Aerodynamic Lift Tensor",
                    accent = AmberClothSecondary,
                    equation = "x_i(t+Δt) = x_i(t) + d·(x_i(t) - x_i(t-Δt)) + a_i Δt²\n" +
                        "F_aero = ½ ρ_air C_d A ((v_wind - v)·n) n\n" +
                        "ε_ij = (|x_i - x_j| - L_0) / L_0  [Fracture if ε > ε_max]",
                    liveSubstitution = String.format(
                        Locale.US,
                        "k_struct = %.2f | Relaxation = %d iters | C_d = %.2f",
                        uiState.clothStructuralStiffness,
                        uiState.clothSolverIterations,
                        uiState.clothAerodynamicDrag
                    )
                )

                EquationBlockCard(
                    title = "3. Symplectic Velocity-Verlet N-Body Gravitation",
                    accent = VioletGravityTertiary,
                    equation = "a_i(t) = Σ_{j≠i} G m_j (r_j - r_i) / (|r_j - r_i|² + ε²)^(3/2)\n" +
                        "v(t+½Δt) = v(t) + ½ a(t)Δt\n" +
                        "r(t+Δt)  = r(t) + v(t+½Δt)Δt",
                    liveSubstitution = String.format(
                        Locale.US,
                        "G = %.2f | Softening ε = %.2f | Precession = %.2f",
                        uiState.gravityConstantG,
                        uiState.gravitySoftening,
                        uiState.gravityRelativisticPrecession
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_close_math_inspector"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanFluidPrimary,
                        contentColor = StudioObsidian
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Return to 3D Simulation Viewport",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun EquationBlockCard(
    title: String,
    accent: Color,
    equation: String,
    liveSubstitution: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(StudioCardElevated, RoundedCornerShape(10.dp))
            .border(1.dp, StudioBorderSubtle, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = accent
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = equation,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .background(StudioObsidian, RoundedCornerShape(6.dp))
                .padding(8.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = liveSubstitution,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondaryMuted
        )
    }
}
