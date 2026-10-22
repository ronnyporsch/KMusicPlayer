using System;
using System.Collections.Generic;
using Jellyfin.Plugin.KMusicPlayer.Configuration;
using MediaBrowser.Common.Configuration;
using MediaBrowser.Common.Plugins;
using MediaBrowser.Model.Plugins;
using MediaBrowser.Model.Serialization;

namespace Jellyfin.Plugin.KMusicPlayer;

/// <summary>
/// Lets KMusicPlayer clients search YouTube Music and download tracks into the server's music library with yt-dlp.
/// </summary>
public class Plugin : BasePlugin<PluginConfiguration>, IHasWebPages
{
    public Plugin(IApplicationPaths applicationPaths, IXmlSerializer xmlSerializer)
        : base(applicationPaths, xmlSerializer)
    {
        Instance = this;
    }

    public override string Name => "KMusicPlayer";

    public override Guid Id => Guid.Parse("38651385-45e3-4f8e-bc70-c45f5910a4a0");

    public override string Description => "Searches YouTube Music for KMusicPlayer clients and downloads the tracks they pick into the music library using yt-dlp.";

    public static Plugin? Instance { get; private set; }

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
