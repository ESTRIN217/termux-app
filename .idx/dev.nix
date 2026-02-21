{ pkgs, ... }: {
  # Canal de paquetes
  channel = "stable-24.11";

  # Herramientas básicas del sistema
  packages = [
    pkgs.jdk17
    pkgs.android-tools
    pkgs.gradle
  ];

  # Variables de entorno
  env = {
    JAVA_HOME = "${pkgs.jdk17}";
  };

  idx = {
    # Extensiones para tu desarrollo en Kotlin y Java
    extensions = [
      "redhat.java"
      "vscjava.vscode-java-pack"
      "vscjava.vscode-gradle"
      "fwcd.kotlin"
      "kotlin_jetbrains.kotlin"
    ];

    # Configuración del entorno de trabajo (Mantenlo dentro de idx)
    workspace = {
      onCreate = {
        # Instalamos el NDK exacto que necesitas (29.0.14206865)
        install-ndk = "yes | sdkmanager --install 'ndk;29.0.14206865'";
        
        # Opcional: Aceptar todas las licencias del SDK
        accept-licenses = "yes | sdkmanager --licenses";
      };
      
      onStart = {
        # Aquí puedes poner comandos que quieras que corran cada vez que abras el proyecto
      };
    };

    # Panel de previsualización de Android
    previews = {
      enable = true;
      previews = {
        android = {
          manager = "android";
        };
      };
    };
  };
}