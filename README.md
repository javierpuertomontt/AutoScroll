# AutoScroll v12 para Android

Aplicación Android con la misma interfaz compacta de cuatro controles flotantes: **▲ desplazar hacia arriba**, **⏸ pausar**, **▼ desplazar hacia abajo** y **✕ cerrar**.

## Cambios de esta versión

- Paquete nuevo `com.javier.autoscroll.v12`, para evitar el rechazo de instalación por conflicto de firma con la app anterior.
- Controles flotantes con toque y arrastre gestionados por cada botón; un toque ejecuta la acción y arrastrar mueve el panel.
- Motor de gestos secuencial: espera el resultado de cada gesto antes de enviar el siguiente para evitar que el desplazamiento quede bloqueado.
- Servicio en primer plano para mantener el panel flotante activo.
- Pantalla de inicio que indica si faltan permisos.

## Compilar y descargar

Cada cambio en `main` inicia una compilación en GitHub Actions. Abre la pestaña **Actions**, entra en la ejecución más reciente y descarga el artefacto **AutoScroll-v12-debug**. Dentro del ZIP está `app-debug.apk`.

## Instalar en Xiaomi / HyperOS

1. Instala el APK de v12. Se puede mantener instalada la versión anterior porque esta versión usa un identificador de paquete diferente.
2. Abre **AutoScroll v12**.
3. Entra en **Activar accesibilidad** y habilita el servicio de AutoScroll v12.
4. Concede **Mostrar sobre otras apps**.
5. Regresa a AutoScroll v12 y pulsa **Abrir controles flotantes**.
6. En la app que quieras desplazar, usa ▲ o ▼. ⏸ pausa y ✕ cierra el panel.

La compilación automática comprueba que el proyecto compila; las pruebas finales de interacción deben hacerse en el teléfono real.
