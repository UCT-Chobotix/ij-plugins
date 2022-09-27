# ij-plugins
Chobotix plugins for ImageJ package.

You can import this project into your favorite IDE:

  * Eclipse: File > Import > Existing Maven Projects
  * NetBeans: File > Open Project
  * IDEA: File > Open Project... (select pom.xml)

Each subdirectory contains an individual plugin. 
You can also build each plugin in the corresponding subdirectory using command line:

`cd solvatochromic-shift`

`mvn package`

Resulting jar is then in `target` subdirectory and you can simply copy it to directory `plugins` in ImageJ installation, for example:

`cp target/solvatochromic-shift-1.0.jar /Applications/Fiji.app/plugins/`

Plugin is then available after ImageJ restart in ImageJ menu in `Plugins->[PluginMenuName]`, where [PluginMenuName] is defined in `@Plugin` annotation of the plugin main class, for example `Plugins->Solvatochromic shift`.
