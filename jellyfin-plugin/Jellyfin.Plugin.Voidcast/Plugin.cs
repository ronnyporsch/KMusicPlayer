using System;
using System.Collections.Generic;
using System.IO;
using Jellyfin.Plugin.Voidcast.Configuration;
using MediaBrowser.Common.Configuration;
using MediaBrowser.Common.Plugins;
using MediaBrowser.Model.Plugins;
using MediaBrowser.Model.Serialization;

namespace Jellyfin.Plugin.Voidcast;

/// <summary>
/// Lets Voidcast clients search YouTube Music and download tracks into the server's music library with yt-dlp.
/// </summary>
public class Plugin : BasePlugin<PluginConfiguration>, IHasWebPages
{
    public Plugin(IApplicationPaths applicationPaths, IXmlSerializer xmlSerializer)
        : base(applicationPaths, xmlSerializer)
    {
        Instance = this;
        MigrateConfiguration(applicationPaths);
    }

    public override string Name => "Voidcast";

    public override Guid Id => Guid.Parse("38651385-45e3-4f8e-bc70-c45f5910a4a0");

    public override string Description => "Searches YouTube Music for Voidcast clients and downloads the tracks they pick into the music library using yt-dlp.";

    public static Plugin? Instance { get; private set; }

    /// <summary>
    /// The configuration file is named after the assembly, so copies the one of the plugin's former name, KMusicPlayer.
    /// </summary>
    private static void MigrateConfiguration(IApplicationPaths applicationPaths)
    {
        var path = Path.Combine(applicationPaths.PluginConfigurationsPath, "Jellyfin.Plugin.Voidcast.xml");
        var oldPath = Path.Combine(applicationPaths.PluginConfigurationsPath, "Jellyfin.Plugin.KMusicPlayer.xml");
        if (!File.Exists(path) && File.Exists(oldPath))
        {
            File.Copy(oldPath, path);
        }
    }

    public IEnumerable<PluginPageInfo> GetPages()
    {
        return
        [
            new PluginPageInfo
            {
                Name = Name,
                EmbeddedResourcePath = GetType().Namespace + ".Configuration.configPage.html",
            },
        ];
    }
}
