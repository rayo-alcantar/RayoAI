package com.rayoai.presentation.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Composable para un campo de texto seguro, típicamente usado para contraseñas o claves API.
 * Permite alternar la visibilidad del texto y utiliza un teclado de tipo contraseña.
 * @param value El valor actual del campo de texto.
 * @param onValueChange Callback que se invoca cuando el valor del campo de texto cambia.
 * @param label La etiqueta (hint) del campo de texto.
 * @param modifier Modificador para aplicar al Composable.
 */
@Composable
fun SecureTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    // Estado para controlar la visibilidad del texto (contraseña).
    var passwordVisible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        // Configuración del teclado para tipo contraseña.
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        // Transformación visual: oculta el texto si `passwordVisible` es `false`.
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        // Icono al final del campo para alternar la visibilidad.
        trailingIcon = {
            val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
            val description = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña"
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(imageVector = image, contentDescription = description)
            }
        },
        modifier = modifier
    )
}