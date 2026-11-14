# Vidtube para Samsung Smart TV (Tizen)

## Formato

- Los televisores Samsung no instalan APK de Android.
- Vidtube TV es una **app web de Tizen (`.wgt`)**:
  - Configuración en `tv/config.xml`.
  - Paquete `VidtubeSlv`, app `VidtubeSlv.Vidtube`.

## Compatibilidad

El código se compila para Chromium 69 (`pc/build-tv.mjs`, `target: chrome69` + `core-js`). La CSS evita `gap` en flex, `inset`, `aspect-ratio` y `:has`.

Según la tabla de motores web de Samsung:

| Año del TV | Tizen | Motor web | Soporte |
|---|---|---|---|
| 2025 | 9.0 | Chromium M120 | Sí |
| 2024 | 8.0 | M108 | Sí |
| 2023 | 7.0 | M94 | Sí |
| 2022 | 6.5 | M85 | Sí |
| 2021 | 6.0 | M76 | Sí |
| 2020 | 5.5 | M69 | Sí (objetivo mínimo) |
| 2019 o anterior | 5.0 o menos | M63 o menos | No probado |

**Cómo saber el año del TV:**
- El código de modelo en la etiqueta trasera. Ejemplos: UN55**TU**7000 = 2020, **AU** = 2021, **BU** = 2022, **CU** = 2023, **DU** = 2024.
- O en el TV: Configuración › Asistencia › Acerca de este TV.

## Instalación de prueba

1. **Activa el Modo desarrollador en el TV.**
   1. Abre Apps.
   2. Con el control, marca **1 2 3 4 5**.
   3. Activa *Developer mode*.
   4. Escribe la IP de tu PC.
   5. Reinicia el TV.
2. **Descarga el archivo.** En GitHub, en la última release `tv-1.0.N`, descarga `Vidtube-TV-1.0.N.wgt`.
3. **Instala el `.wgt`.** Elige una de estas dos formas:
   - **Recomendada:** con **Apps2Samsung** (Jellyfin2Samsung), usando la opción de instalar un `.wgt` propio. El programa firma el paquete con el certificado que el TV necesita.
   - **Con Tizen Studio:**
     1. Abre Device Manager.
     2. Agrega el TV con su IP y actívalo.
     3. Crea un perfil de certificado **Samsung** para TV con tu cuenta Samsung. Si la firma no es válida, sale el error 1010.
     4. Instala con `tizen install -n Vidtube-TV-1.0.N.wgt -t <TV>`.

## Control remoto

- **Flechas:** mueven el foco al elemento más cercano en esa dirección.
- **Arriba/abajo:** no saltan entre el menú y el contenido. Para pasar al menú se usa izquierda.
- **OK:** abre.
- **Back:** vuelve. En la primera pantalla, sale de la app.
- **Botones multimedia** (reproducir, pausar, adelantar, retroceder): se registran con `tizen.tvinputdevice`.

## Pendiente de verificar en un TV real

- La reproducción en un TV real. En las pruebas automáticas, YouTube bloquea la IP del servidor de pruebas.
- Si un modelo no reproduce, la alternativa es **TizenTube**.
