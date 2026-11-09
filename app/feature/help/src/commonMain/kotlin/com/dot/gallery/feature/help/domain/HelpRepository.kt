/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.help.domain

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.FormatAlignRight
import androidx.compose.material.icons.automirrored.outlined.Launch
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.MergeType
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.automirrored.outlined.ViewQuilt
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import com.dot.gallery.feature.help.generated.resources.help_tip_view_codes_title
import com.dot.gallery.feature.help.generated.resources.help_tip_view_codes_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_view_codes_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_view_codes_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_view_codes_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_view_codes_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_view_codes_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_view_codes_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_view_codes_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_view_codes_p2_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_view_codes_p2_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_view_codes_p2_s3
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.BrightnessHigh
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.BurstMode
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CalendarViewMonth
import androidx.compose.material.icons.outlined.CallToAction
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DashboardCustomize
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DesignServices
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.EnhancedEncryption
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.GifBox
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HdrOn
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.InvertColors
import androidx.compose.material.icons.outlined.LibraryAddCheck
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MotionPhotosOn
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.OndemandVideo
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Panorama
import androidx.compose.material.icons.outlined.PermMedia
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoFilter
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Pinch
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RawOn
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.Start
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Swipe
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material.icons.outlined.VideoSettings
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.outlined.Workspaces
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOutMap
import com.dot.gallery.feature.help.generated.resources.*
import com.dot.gallery.feature.help.generated.resources.Res
import com.dot.gallery.feature.help.generated.resources.help_action_open_settings
import com.dot.gallery.feature.help.generated.resources.help_qa_open_albums
import com.dot.gallery.feature.help.generated.resources.help_qa_open_categories
import com.dot.gallery.feature.help.generated.resources.help_qa_open_favorites
import com.dot.gallery.feature.help.generated.resources.help_qa_open_library
import com.dot.gallery.feature.help.generated.resources.help_qa_open_locations
import com.dot.gallery.feature.help.generated.resources.help_qa_open_search
import com.dot.gallery.feature.help.generated.resources.help_qa_open_timeline
import com.dot.gallery.feature.help.generated.resources.help_qa_open_trash
import com.dot.gallery.feature.help.generated.resources.help_qa_open_vault
import com.dot.gallery.feature.help.generated.resources.help_tip_accessibility_font_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_accessibility_font_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_accessibility_font_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_accessibility_font_title
import com.dot.gallery.feature.help.generated.resources.help_tip_accessibility_remote_settings_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_accessibility_remote_settings_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_accessibility_remote_settings_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_accessibility_remote_settings_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_accessibility_remote_settings_p1_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_accessibility_remote_settings_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_accessibility_remote_settings_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_accessibility_remote_settings_title
import com.dot.gallery.feature.help.generated.resources.help_tip_action_add_collection_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_action_add_collection_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_action_add_collection_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_action_add_collection_title
import com.dot.gallery.feature.help.generated.resources.help_tip_action_copy_to_album_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_action_copy_to_album_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_action_copy_to_album_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_action_copy_to_album_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_action_copy_to_album_p1_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_action_copy_to_album_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_action_copy_to_album_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_action_copy_to_album_title
import com.dot.gallery.feature.help.generated.resources.help_tip_action_edit_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_action_edit_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_action_edit_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_action_edit_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_action_edit_p1_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_action_edit_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_action_edit_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_action_edit_title
import com.dot.gallery.feature.help.generated.resources.help_tip_action_hide_vault_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_action_hide_vault_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_action_hide_vault_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_action_hide_vault_p2_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_action_hide_vault_p2_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_action_hide_vault_p2_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_action_hide_vault_p2_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_action_hide_vault_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_action_hide_vault_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_action_hide_vault_title
import com.dot.gallery.feature.help.generated.resources.help_tip_action_move_to_album_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_action_move_to_album_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_action_move_to_album_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_action_move_to_album_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_action_move_to_album_p1_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_action_move_to_album_p1_s5
import com.dot.gallery.feature.help.generated.resources.help_tip_action_move_to_album_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_action_move_to_album_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_action_move_to_album_title
import com.dot.gallery.feature.help.generated.resources.help_tip_action_rename_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_action_rename_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_action_rename_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_action_rename_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_action_rename_p1_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_action_rename_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_action_rename_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_action_rename_title
import com.dot.gallery.feature.help.generated.resources.help_tip_action_save_motion_video_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_action_save_motion_video_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_action_save_motion_video_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_action_save_motion_video_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_action_save_motion_video_p1_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_action_save_motion_video_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_action_save_motion_video_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_action_save_motion_video_title
import com.dot.gallery.feature.help.generated.resources.help_tip_action_share_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_action_share_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_action_share_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_action_share_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_action_share_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_action_share_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_action_share_title
import com.dot.gallery.feature.help.generated.resources.help_tip_action_view_all_metadata_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_action_view_all_metadata_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_action_view_all_metadata_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_action_view_all_metadata_title
import com.dot.gallery.feature.help.generated.resources.help_tip_action_visual_search_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_action_visual_search_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_action_visual_search_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_action_visual_search_p2_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_action_visual_search_p2_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_action_visual_search_p2_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_action_visual_search_p2_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_action_visual_search_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_action_visual_search_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_action_visual_search_title
import com.dot.gallery.feature.help.generated.resources.help_tip_advanced_developer_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_advanced_developer_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_advanced_developer_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_advanced_developer_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_advanced_developer_p1_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_advanced_developer_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_advanced_developer_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_advanced_developer_title
import com.dot.gallery.feature.help.generated.resources.help_tip_advanced_media_picker_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_advanced_media_picker_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_advanced_media_picker_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_advanced_media_picker_title
import com.dot.gallery.feature.help.generated.resources.help_tip_advanced_standalone_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_advanced_standalone_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_advanced_standalone_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_advanced_standalone_title
import com.dot.gallery.feature.help.generated.resources.help_tip_advanced_wallpaper_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_advanced_wallpaper_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_advanced_wallpaper_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_advanced_wallpaper_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_advanced_wallpaper_p1_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_advanced_wallpaper_p1_s5
import com.dot.gallery.feature.help.generated.resources.help_tip_advanced_wallpaper_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_advanced_wallpaper_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_advanced_wallpaper_title
import com.dot.gallery.feature.help.generated.resources.help_tip_ai_categories_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_ai_categories_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_ai_categories_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_ai_categories_p2_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_ai_categories_p2_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_ai_categories_p2_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_ai_categories_p2_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_ai_categories_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_ai_categories_p3_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_ai_categories_p3_title
import com.dot.gallery.feature.help.generated.resources.help_tip_ai_categories_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_ai_categories_title
import com.dot.gallery.feature.help.generated.resources.help_tip_ai_create_category_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_ai_create_category_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_ai_create_category_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_ai_create_category_p2_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_ai_create_category_p2_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_ai_create_category_p2_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_ai_create_category_p2_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_ai_create_category_p2_s5
import com.dot.gallery.feature.help.generated.resources.help_tip_ai_create_category_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_ai_create_category_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_ai_create_category_title
import com.dot.gallery.feature.help.generated.resources.help_tip_ai_indexing_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_ai_indexing_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_ai_indexing_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_ai_indexing_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_ai_indexing_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_ai_indexing_title
import com.dot.gallery.feature.help.generated.resources.help_tip_ai_models_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_ai_models_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_ai_models_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_ai_models_p2_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_ai_models_p2_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_ai_models_p2_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_ai_models_p2_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_ai_models_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_ai_models_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_ai_models_title
import com.dot.gallery.feature.help.generated.resources.help_tip_album_cover_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_album_cover_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_album_cover_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_album_cover_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_album_cover_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_album_cover_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_album_cover_title
import com.dot.gallery.feature.help.generated.resources.help_tip_album_picker_search_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_album_picker_search_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_album_picker_search_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_album_picker_search_title
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_browse_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_browse_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_browse_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_browse_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_browse_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_browse_title
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_collections_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_collections_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_collections_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_collections_p2_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_collections_p2_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_collections_p2_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_collections_p2_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_collections_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_collections_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_collections_title
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_groups_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_groups_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_groups_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_groups_p2_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_groups_p2_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_groups_p2_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_groups_p2_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_groups_p2_s5
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_groups_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_groups_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_groups_title
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_hide_timeline_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_hide_timeline_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_hide_timeline_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_hide_timeline_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_hide_timeline_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_hide_timeline_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_hide_timeline_title
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_ignore_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_ignore_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_ignore_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_ignore_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_ignore_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_ignore_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_ignore_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_ignore_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_ignore_title
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_ignored_manage_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_ignored_manage_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_ignored_manage_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_ignored_manage_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_ignored_manage_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_ignored_manage_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_ignored_manage_title
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_merge_by_name_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_merge_by_name_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_merge_by_name_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_merge_by_name_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_merge_by_name_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_merge_by_name_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_merge_by_name_title
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_pin_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_pin_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_pin_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_pin_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_pin_p1_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_pin_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_pin_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_pin_title
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_sections_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_sections_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_sections_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_sections_p2_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_sections_p2_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_sections_p2_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_sections_p2_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_sections_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_sections_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_sections_title
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_thumbnails_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_thumbnails_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_thumbnails_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_thumbnails_p2_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_thumbnails_p2_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_thumbnails_p2_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_thumbnails_p2_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_thumbnails_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_thumbnails_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_albums_thumbnails_title
import com.dot.gallery.feature.help.generated.resources.help_tip_animated_formats_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_animated_formats_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_animated_formats_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_animated_formats_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_animated_formats_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_animated_formats_title
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_copy_clipboard_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_copy_clipboard_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_copy_clipboard_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_copy_clipboard_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_copy_clipboard_p1_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_copy_clipboard_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_copy_clipboard_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_copy_clipboard_title
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_select_media_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_select_media_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_select_media_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_select_media_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_select_media_p1_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_select_media_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_select_media_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_select_media_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_select_media_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_select_media_title
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_setup_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_setup_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_setup_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_setup_title
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_share_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_share_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_share_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_share_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_share_p1_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_share_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_share_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_share_title
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_timeline_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_timeline_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_timeline_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_timeline_p2_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_timeline_p2_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_timeline_p2_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_timeline_p2_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_timeline_p2_s5
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_timeline_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_timeline_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_timeline_title
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_view_media_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_view_media_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_view_media_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_view_media_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_view_media_p3_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_view_media_p3_title
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_view_media_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_basics_view_media_title
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_add_server_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_add_server_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_add_server_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_add_server_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_add_server_p1_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_add_server_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_add_server_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_add_server_title
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_albums_manage_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_albums_manage_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_albums_manage_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_albums_manage_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_albums_manage_p1_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_albums_manage_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_albums_manage_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_albums_manage_title
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_backup_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_backup_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_backup_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_backup_p2_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_backup_p2_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_backup_p2_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_backup_p2_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_backup_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_backup_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_backup_title
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_connect_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_connect_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_connect_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_connect_p2_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_connect_p2_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_connect_p2_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_connect_p2_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_connect_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_connect_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_connect_title
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_delete_scope_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_delete_scope_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_delete_scope_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_delete_scope_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_delete_scope_p1_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_delete_scope_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_delete_scope_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_delete_scope_title
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_destinations_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_destinations_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_destinations_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_destinations_title
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_explore_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_explore_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_explore_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_explore_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_explore_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_explore_title
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_free_up_space_auto_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_free_up_space_auto_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_free_up_space_auto_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_free_up_space_auto_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_free_up_space_auto_p1_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_free_up_space_auto_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_free_up_space_auto_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_free_up_space_auto_title
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_free_up_space_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_free_up_space_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_free_up_space_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_free_up_space_title
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_nextcloud_login_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_nextcloud_login_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_nextcloud_login_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_nextcloud_login_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_nextcloud_login_p1_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_nextcloud_login_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_nextcloud_login_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_nextcloud_login_title
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_people_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_people_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_people_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_people_title
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_provider_settings_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_provider_settings_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_provider_settings_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_provider_settings_title
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_shared_links_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_shared_links_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_shared_links_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_shared_links_title
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_smart_search_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_smart_search_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_smart_search_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_smart_search_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_smart_search_p1_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_smart_search_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_smart_search_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_smart_search_title
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_sync_status_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_sync_status_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_sync_status_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_sync_status_title
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_timeline_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_timeline_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_timeline_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_timeline_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_timeline_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_timeline_title
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_transfer_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_transfer_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_transfer_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_transfer_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_transfer_p1_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_transfer_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_transfer_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_transfer_title
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_two_way_sync_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_two_way_sync_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_two_way_sync_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_two_way_sync_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_two_way_sync_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_two_way_sync_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_two_way_sync_title
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_upload_paths_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_upload_paths_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_upload_paths_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_upload_paths_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_upload_paths_p1_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_upload_paths_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_upload_paths_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_cloud_upload_paths_title
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_adjustments_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_adjustments_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_adjustments_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_adjustments_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_adjustments_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_adjustments_title
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_backups_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_backups_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_backups_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_backups_p2_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_backups_p2_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_backups_p2_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_backups_p2_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_backups_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_backups_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_backups_title
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_crop_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_crop_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_crop_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_crop_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_crop_p3_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_crop_p3_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_crop_p3_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_crop_p3_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_crop_p3_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_crop_p3_s5
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_crop_p3_title
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_crop_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_crop_title
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_effects_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_effects_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_effects_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_effects_p2_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_effects_p2_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_effects_p2_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_effects_p2_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_effects_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_effects_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_effects_title
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_filters_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_filters_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_filters_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_filters_p2_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_filters_p2_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_filters_p2_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_filters_p2_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_filters_p2_s5
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_filters_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_filters_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_filters_title
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_markup_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_markup_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_markup_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_markup_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_markup_p3_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_markup_p3_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_markup_p3_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_markup_p3_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_markup_p3_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_markup_p3_s5
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_markup_p3_s6
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_markup_p3_title
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_markup_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_markup_title
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_rotate_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_rotate_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_rotate_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_rotate_p2_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_rotate_p2_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_rotate_p2_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_rotate_p2_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_rotate_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_rotate_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_rotate_title
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_save_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_save_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_save_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_save_p2_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_save_p2_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_save_p2_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_save_p2_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_save_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_save_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_edit_save_title
import com.dot.gallery.feature.help.generated.resources.help_tip_exif_delete_location_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_exif_delete_location_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_exif_delete_location_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_exif_delete_location_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_exif_delete_location_p1_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_exif_delete_location_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_exif_delete_location_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_exif_delete_location_title
import com.dot.gallery.feature.help.generated.resources.help_tip_exif_edit_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_exif_edit_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_exif_edit_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_exif_edit_p2_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_exif_edit_p2_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_exif_edit_p2_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_exif_edit_p2_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_exif_edit_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_exif_edit_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_exif_edit_title
import com.dot.gallery.feature.help.generated.resources.help_tip_exif_refresh_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_exif_refresh_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_exif_refresh_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_exif_refresh_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_exif_refresh_p1_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_exif_refresh_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_exif_refresh_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_exif_refresh_title
import com.dot.gallery.feature.help.generated.resources.help_tip_exif_view_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_exif_view_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_exif_view_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_exif_view_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_exif_view_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_exif_view_title
import com.dot.gallery.feature.help.generated.resources.help_tip_fav_add_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_fav_add_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_fav_add_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_fav_add_title
import com.dot.gallery.feature.help.generated.resources.help_tip_fav_browse_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_fav_browse_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_fav_browse_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_fav_browse_title
import com.dot.gallery.feature.help.generated.resources.help_tip_gesture_long_press_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_gesture_long_press_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_gesture_long_press_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_gesture_long_press_title
import com.dot.gallery.feature.help.generated.resources.help_tip_gesture_multi_select_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_gesture_multi_select_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_gesture_multi_select_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_gesture_multi_select_title
import com.dot.gallery.feature.help.generated.resources.help_tip_gesture_pinch_grid_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_gesture_pinch_grid_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_gesture_pinch_grid_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_gesture_pinch_grid_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_gesture_pinch_grid_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_gesture_pinch_grid_title
import com.dot.gallery.feature.help.generated.resources.help_tip_gesture_swipe_viewer_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_gesture_swipe_viewer_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_gesture_swipe_viewer_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_gesture_swipe_viewer_title
import com.dot.gallery.feature.help.generated.resources.help_tip_library_sections_edit_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_library_sections_edit_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_library_sections_edit_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_library_sections_edit_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_library_sections_edit_p1_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_library_sections_edit_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_library_sections_edit_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_library_sections_edit_title
import com.dot.gallery.feature.help.generated.resources.help_tip_location_browse_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_location_browse_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_location_browse_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_location_browse_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_location_browse_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_location_browse_title
import com.dot.gallery.feature.help.generated.resources.help_tip_location_viewer_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_location_viewer_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_location_viewer_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_location_viewer_title
import com.dot.gallery.feature.help.generated.resources.help_tip_media_type_albums_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_media_type_albums_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_media_type_albums_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_media_type_albums_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_media_type_albums_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_media_type_albums_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_media_type_albums_title
import com.dot.gallery.feature.help.generated.resources.help_tip_metadata_sanitize_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_metadata_sanitize_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_metadata_sanitize_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_metadata_sanitize_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_metadata_sanitize_p1_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_metadata_sanitize_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_metadata_sanitize_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_metadata_sanitize_title
import com.dot.gallery.feature.help.generated.resources.help_tip_motion_photo_frames_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_motion_photo_frames_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_motion_photo_frames_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_motion_photo_frames_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_motion_photo_frames_p1_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_motion_photo_frames_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_motion_photo_frames_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_motion_photo_frames_title
import com.dot.gallery.feature.help.generated.resources.help_tip_nav_albums_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_nav_albums_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_nav_albums_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_nav_albums_p2_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_nav_albums_p2_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_nav_albums_p2_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_nav_albums_p2_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_nav_albums_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_nav_albums_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_nav_albums_title
import com.dot.gallery.feature.help.generated.resources.help_tip_nav_auto_hide_bars_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_nav_auto_hide_bars_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_nav_auto_hide_bars_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_nav_auto_hide_bars_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_nav_auto_hide_bars_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_nav_auto_hide_bars_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_nav_auto_hide_bars_title
import com.dot.gallery.feature.help.generated.resources.help_tip_nav_bottom_bar_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_nav_bottom_bar_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_nav_bottom_bar_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_nav_bottom_bar_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_nav_bottom_bar_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_nav_bottom_bar_title
import com.dot.gallery.feature.help.generated.resources.help_tip_nav_launch_screen_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_nav_launch_screen_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_nav_launch_screen_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_nav_launch_screen_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_nav_launch_screen_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_nav_launch_screen_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_nav_launch_screen_title
import com.dot.gallery.feature.help.generated.resources.help_tip_nav_library_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_nav_library_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_nav_library_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_nav_library_title
import com.dot.gallery.feature.help.generated.resources.help_tip_nav_old_navbar_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_nav_old_navbar_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_nav_old_navbar_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_nav_old_navbar_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_nav_old_navbar_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_nav_old_navbar_title
import com.dot.gallery.feature.help.generated.resources.help_tip_nav_search_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_nav_search_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_nav_search_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_nav_search_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_nav_search_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_nav_search_title
import com.dot.gallery.feature.help.generated.resources.help_tip_people_fix_faces_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_people_fix_faces_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_people_fix_faces_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_people_fix_faces_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_people_fix_faces_p1_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_people_fix_faces_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_people_fix_faces_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_people_fix_faces_title
import com.dot.gallery.feature.help.generated.resources.help_tip_people_grouping_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_people_grouping_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_people_grouping_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_people_grouping_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_people_grouping_p1_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_people_grouping_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_people_grouping_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_people_grouping_title
import com.dot.gallery.feature.help.generated.resources.help_tip_people_manage_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_people_manage_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_people_manage_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_people_manage_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_people_manage_p1_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_people_manage_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_people_manage_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_people_manage_title
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_amoled_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_amoled_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_amoled_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_amoled_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_amoled_p1_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_amoled_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_amoled_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_amoled_title
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_app_logo_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_app_logo_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_app_logo_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_app_logo_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_app_logo_p1_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_app_logo_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_app_logo_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_app_logo_title
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_app_name_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_app_name_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_app_name_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_app_name_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_app_name_p1_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_app_name_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_app_name_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_app_name_title
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_blur_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_blur_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_blur_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_blur_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_blur_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_blur_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_blur_title
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_colors_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_colors_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_colors_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_colors_p2_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_colors_p2_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_colors_p2_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_colors_p2_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_colors_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_colors_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_colors_title
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_dark_mode_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_dark_mode_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_dark_mode_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_dark_mode_title
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_grid_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_grid_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_grid_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_grid_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_grid_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_grid_title
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_shared_elements_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_shared_elements_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_shared_elements_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_shared_elements_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_shared_elements_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_shared_elements_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_shared_elements_title
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_theme_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_theme_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_theme_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_theme_p2_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_theme_p2_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_theme_p2_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_theme_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_theme_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_personalize_theme_title
import com.dot.gallery.feature.help.generated.resources.help_tip_raw_develop_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_raw_develop_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_raw_develop_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_raw_develop_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_raw_develop_p1_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_raw_develop_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_raw_develop_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_raw_develop_title
import com.dot.gallery.feature.help.generated.resources.help_tip_search_ai_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_search_ai_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_search_ai_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_search_ai_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_search_ai_p3_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_search_ai_p3_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_search_ai_p3_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_search_ai_p3_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_search_ai_p3_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_search_ai_p3_title
import com.dot.gallery.feature.help.generated.resources.help_tip_search_ai_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_search_ai_title
import com.dot.gallery.feature.help.generated.resources.help_tip_search_history_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_search_history_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_search_history_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_search_history_title
import com.dot.gallery.feature.help.generated.resources.help_tip_search_ignored_albums_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_search_ignored_albums_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_search_ignored_albums_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_search_ignored_albums_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_search_ignored_albums_p1_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_search_ignored_albums_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_search_ignored_albums_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_search_ignored_albums_title
import com.dot.gallery.feature.help.generated.resources.help_tip_search_indexing_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_search_indexing_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_search_indexing_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_search_indexing_title
import com.dot.gallery.feature.help.generated.resources.help_tip_search_metadata_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_search_metadata_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_search_metadata_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_search_metadata_title
import com.dot.gallery.feature.help.generated.resources.help_tip_search_text_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_search_text_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_search_text_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_search_text_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_search_text_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_search_text_title
import com.dot.gallery.feature.help.generated.resources.help_tip_security_aapm_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_security_aapm_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_security_aapm_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_security_aapm_p2_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_security_aapm_p2_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_security_aapm_p2_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_security_aapm_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_security_aapm_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_security_aapm_title
import com.dot.gallery.feature.help.generated.resources.help_tip_security_encrypted_storage_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_security_encrypted_storage_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_security_encrypted_storage_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_security_encrypted_storage_p2_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_security_encrypted_storage_p2_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_security_encrypted_storage_p2_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_security_encrypted_storage_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_security_encrypted_storage_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_security_encrypted_storage_title
import com.dot.gallery.feature.help.generated.resources.help_tip_security_private_folder_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_security_private_folder_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_security_private_folder_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_security_private_folder_p2_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_security_private_folder_p2_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_security_private_folder_p2_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_security_private_folder_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_security_private_folder_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_security_private_folder_title
import com.dot.gallery.feature.help.generated.resources.help_tip_security_sandbox_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_security_sandbox_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_security_sandbox_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_security_sandbox_p2_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_security_sandbox_p2_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_security_sandbox_p2_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_security_sandbox_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_security_sandbox_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_security_sandbox_title
import com.dot.gallery.feature.help.generated.resources.help_tip_selection_batch_delete_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_selection_batch_delete_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_selection_batch_delete_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_selection_batch_delete_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_selection_batch_delete_p1_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_selection_batch_delete_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_selection_batch_delete_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_selection_batch_delete_title
import com.dot.gallery.feature.help.generated.resources.help_tip_selection_batch_share_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_selection_batch_share_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_selection_batch_share_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_selection_batch_share_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_selection_batch_share_p1_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_selection_batch_share_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_selection_batch_share_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_selection_batch_share_title
import com.dot.gallery.feature.help.generated.resources.help_tip_selection_right_align_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_selection_right_align_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_selection_right_align_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_selection_right_align_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_selection_right_align_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_selection_right_align_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_selection_right_align_title
import com.dot.gallery.feature.help.generated.resources.help_tip_selection_sheet_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_selection_sheet_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_selection_sheet_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_selection_sheet_p2_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_selection_sheet_p2_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_selection_sheet_p2_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_selection_sheet_p2_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_selection_sheet_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_selection_sheet_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_selection_sheet_title
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_ai_models_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_ai_models_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_ai_models_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_ai_models_p2_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_ai_models_p2_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_ai_models_p2_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_ai_models_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_ai_models_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_ai_models_title
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_amoled_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_amoled_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_amoled_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_amoled_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_amoled_p1_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_amoled_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_amoled_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_amoled_title
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_backup_restore_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_backup_restore_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_backup_restore_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_backup_restore_p2_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_backup_restore_p2_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_backup_restore_p2_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_backup_restore_p2_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_backup_restore_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_backup_restore_p3_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_backup_restore_p3_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_backup_restore_p3_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_backup_restore_p3_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_backup_restore_p3_title
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_backup_restore_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_backup_restore_title
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_color_palette_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_color_palette_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_color_palette_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_color_palette_p2_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_color_palette_p2_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_color_palette_p2_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_color_palette_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_color_palette_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_color_palette_title
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_dark_mode_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_dark_mode_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_dark_mode_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_dark_mode_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_dark_mode_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_dark_mode_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_dark_mode_title
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_date_format_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_date_format_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_date_format_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_date_format_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_date_format_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_date_format_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_date_format_title
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_edit_backups_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_edit_backups_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_edit_backups_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_edit_backups_p2_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_edit_backups_p2_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_edit_backups_p2_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_edit_backups_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_edit_backups_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_edit_backups_title
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_launch_screen_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_launch_screen_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_launch_screen_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_launch_screen_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_launch_screen_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_launch_screen_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_launch_screen_title
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_old_navbar_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_old_navbar_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_old_navbar_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_old_navbar_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_old_navbar_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_old_navbar_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_old_navbar_title
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_secure_mode_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_secure_mode_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_secure_mode_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_secure_mode_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_secure_mode_p1_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_secure_mode_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_secure_mode_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_secure_mode_title
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_selection_actions_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_selection_actions_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_selection_actions_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_selection_actions_p2_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_selection_actions_p2_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_selection_actions_p2_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_selection_actions_p2_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_selection_actions_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_selection_actions_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_selection_actions_title
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_selection_titles_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_selection_titles_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_selection_titles_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_selection_titles_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_selection_titles_p1_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_selection_titles_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_selection_titles_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_selection_titles_title
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_slideshow_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_slideshow_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_slideshow_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_slideshow_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_slideshow_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_slideshow_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_slideshow_title
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_trash_confirm_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_trash_confirm_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_trash_confirm_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_trash_confirm_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_trash_confirm_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_trash_confirm_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_trash_confirm_title
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_trash_toggle_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_trash_toggle_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_trash_toggle_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_trash_toggle_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_trash_toggle_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_trash_toggle_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_trash_toggle_title
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_vibrations_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_vibrations_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_vibrations_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_vibrations_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_vibrations_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_vibrations_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_settings_vibrations_title
import com.dot.gallery.feature.help.generated.resources.help_tip_slideshow_play_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_slideshow_play_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_slideshow_play_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_slideshow_play_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_slideshow_play_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_slideshow_play_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_slideshow_play_title
import com.dot.gallery.feature.help.generated.resources.help_tip_story_cards_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_story_cards_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_story_cards_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_story_cards_p2_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_story_cards_p2_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_story_cards_p2_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_story_cards_p2_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_story_cards_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_story_cards_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_story_cards_title
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_capture_date_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_capture_date_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_capture_date_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_capture_date_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_capture_date_p1_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_capture_date_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_capture_date_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_capture_date_title
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_date_header_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_date_header_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_date_header_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_date_header_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_date_header_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_date_header_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_date_header_title
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_fav_icon_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_fav_icon_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_fav_icon_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_fav_icon_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_fav_icon_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_fav_icon_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_fav_icon_title
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_filter_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_filter_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_filter_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_filter_p2_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_filter_p2_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_filter_p2_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_filter_p2_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_filter_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_filter_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_filter_title
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_gif_animation_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_gif_animation_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_gif_animation_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_gif_animation_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_gif_animation_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_gif_animation_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_gif_animation_title
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_group_month_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_group_month_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_group_month_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_group_month_title
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_group_period_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_group_period_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_group_period_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_group_period_p2_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_group_period_p2_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_group_period_p2_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_group_period_p2_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_group_period_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_group_period_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_group_period_title
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_group_similar_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_group_similar_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_group_similar_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_group_similar_p2_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_group_similar_p2_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_group_similar_p2_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_group_similar_p2_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_group_similar_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_group_similar_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_group_similar_title
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_layout_type_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_layout_type_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_layout_type_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_layout_type_p2_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_layout_type_p2_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_layout_type_p2_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_layout_type_p2_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_layout_type_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_layout_type_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_layout_type_title
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_modified_date_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_modified_date_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_modified_date_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_modified_date_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_modified_date_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_modified_date_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_timeline_modified_date_title
import com.dot.gallery.feature.help.generated.resources.help_tip_trash_delete_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_trash_delete_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_trash_delete_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_trash_delete_p2_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_trash_delete_p2_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_trash_delete_p2_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_trash_delete_p2_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_trash_delete_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_trash_delete_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_trash_delete_title
import com.dot.gallery.feature.help.generated.resources.help_tip_trash_enable_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_trash_enable_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_trash_enable_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_trash_enable_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_trash_enable_p1_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_trash_enable_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_trash_enable_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_trash_enable_title
import com.dot.gallery.feature.help.generated.resources.help_tip_trash_fav_redesign_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_trash_fav_redesign_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_trash_fav_redesign_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_trash_fav_redesign_title
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_add_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_add_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_add_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_add_p2_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_add_p2_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_add_p2_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_add_p2_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_add_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_add_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_add_title
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_one_lock_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_one_lock_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_one_lock_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_one_lock_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_one_lock_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_one_lock_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_one_lock_title
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_overhaul_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_overhaul_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_overhaul_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_overhaul_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_overhaul_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_overhaul_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_overhaul_p2_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_overhaul_p2_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_overhaul_p2_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_overhaul_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_overhaul_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_overhaul_title
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_restore_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_restore_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_restore_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_restore_title
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_setup_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_setup_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_setup_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_setup_p2_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_setup_p2_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_setup_p2_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_setup_p2_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_setup_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_setup_p3_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_setup_p3_title
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_setup_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_setup_title
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_video_streaming_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_video_streaming_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_video_streaming_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_vault_video_streaming_title
import com.dot.gallery.feature.help.generated.resources.help_tip_video_fix_black_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_video_fix_black_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_video_fix_black_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_video_fix_black_title
import com.dot.gallery.feature.help.generated.resources.help_tip_video_options_popup_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_video_options_popup_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_video_options_popup_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_video_options_popup_title
import com.dot.gallery.feature.help.generated.resources.help_tip_video_subtitles_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_video_subtitles_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_video_subtitles_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_video_subtitles_p2_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_video_subtitles_p2_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_video_subtitles_p2_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_video_subtitles_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_video_subtitles_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_video_subtitles_title
import com.dot.gallery.feature.help.generated.resources.help_tip_video_zoom_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_video_zoom_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_video_zoom_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_video_zoom_title
import com.dot.gallery.feature.help.generated.resources.help_tip_view_cutout_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_view_cutout_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_view_cutout_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_view_cutout_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_view_cutout_p1_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_view_cutout_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_view_cutout_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_view_cutout_title
import com.dot.gallery.feature.help.generated.resources.help_tip_view_details_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_view_details_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_view_details_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_view_details_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_view_details_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_view_details_title
import com.dot.gallery.feature.help.generated.resources.help_tip_view_full_brightness_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_view_full_brightness_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_view_full_brightness_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_view_full_brightness_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_view_full_brightness_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_view_full_brightness_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_view_full_brightness_title
import com.dot.gallery.feature.help.generated.resources.help_tip_view_group_members_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_view_group_members_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_view_group_members_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_view_group_members_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_view_group_members_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_view_group_members_title
import com.dot.gallery.feature.help.generated.resources.help_tip_view_hdr_video_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_view_hdr_video_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_view_hdr_video_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_view_hdr_video_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_view_hdr_video_p1_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_view_hdr_video_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_view_hdr_video_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_view_hdr_video_title
import com.dot.gallery.feature.help.generated.resources.help_tip_view_lock_image_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_view_lock_image_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_view_lock_image_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_view_lock_image_p2_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_view_lock_image_p2_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_view_lock_image_p2_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_view_lock_image_p2_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_view_lock_image_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_view_lock_image_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_view_lock_image_title
import com.dot.gallery.feature.help.generated.resources.help_tip_view_motion_photo_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_view_motion_photo_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_view_motion_photo_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_view_motion_photo_title
import com.dot.gallery.feature.help.generated.resources.help_tip_view_panorama_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_view_panorama_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_view_panorama_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_view_panorama_title
import com.dot.gallery.feature.help.generated.resources.help_tip_view_swipe_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_view_swipe_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_view_swipe_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_view_swipe_title
import com.dot.gallery.feature.help.generated.resources.help_tip_view_tap_navigation_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_view_tap_navigation_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_view_tap_navigation_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_view_tap_navigation_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_view_tap_navigation_p1_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_view_tap_navigation_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_view_tap_navigation_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_view_tap_navigation_title
import com.dot.gallery.feature.help.generated.resources.help_tip_view_video_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_view_video_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_view_video_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_view_video_p2_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_view_video_p2_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_view_video_p2_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_view_video_p2_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_view_video_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_view_video_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_view_video_title
import com.dot.gallery.feature.help.generated.resources.help_tip_view_zoom_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_view_zoom_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_view_zoom_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_view_zoom_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_view_zoom_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_view_zoom_title
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_auto_contrast_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_auto_contrast_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_auto_contrast_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_auto_contrast_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_auto_contrast_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_auto_contrast_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_auto_contrast_title
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_auto_hide_video_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_auto_hide_video_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_auto_hide_video_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_auto_hide_video_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_auto_hide_video_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_auto_hide_video_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_auto_hide_video_title
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_auto_play_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_auto_play_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_auto_play_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_auto_play_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_auto_play_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_auto_play_title
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_cast_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_cast_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_cast_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_cast_p2_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_cast_p2_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_cast_p2_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_cast_p2_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_cast_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_cast_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_cast_title
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_dark_background_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_dark_background_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_dark_background_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_dark_background_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_dark_background_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_dark_background_title
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_date_header_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_date_header_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_date_header_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_date_header_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_date_header_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_date_header_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_date_header_title
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_default_editor_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_default_editor_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_default_editor_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_default_editor_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_default_editor_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_default_editor_p2_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_default_editor_p2_title
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_default_editor_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_default_editor_title
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_fav_button_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_fav_button_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_fav_button_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_fav_button_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_fav_button_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_fav_button_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_viewer_fav_button_title
import com.dot.gallery.feature.help.generated.resources.help_tip_widget_styles_p1_desc
import com.dot.gallery.feature.help.generated.resources.help_tip_widget_styles_p1_s1
import com.dot.gallery.feature.help.generated.resources.help_tip_widget_styles_p1_s2
import com.dot.gallery.feature.help.generated.resources.help_tip_widget_styles_p1_s3
import com.dot.gallery.feature.help.generated.resources.help_tip_widget_styles_p1_s4
import com.dot.gallery.feature.help.generated.resources.help_tip_widget_styles_p1_title
import com.dot.gallery.feature.help.generated.resources.help_tip_widget_styles_subtitle
import com.dot.gallery.feature.help.generated.resources.help_tip_widget_styles_title


object HelpRepository {

    fun getAllTips(): List<HelpTip> = ALL_TIPS
    fun getTipsByCategory(category: HelpCategory): List<HelpTip> = ALL_TIPS.filter { it.category == category }
    fun getTip(id: String): HelpTip? = ALL_TIPS.find { it.id == id }
    fun getFeaturedTips(): List<HelpTip> = listOf("search_ai", "ai_categories").mapNotNull { getTip(it) }

    fun getGetStartedCategories() = listOf(
        HelpCategory.GET_STARTED_BASICS, HelpCategory.GET_STARTED_NAVIGATION, HelpCategory.GET_STARTED_PERSONALIZATION
    )
    fun getMakeMostCategories() = listOf(
        HelpCategory.TIMELINE_ALBUMS, HelpCategory.VIEWING, HelpCategory.VIEWER_ACTIONS,
        HelpCategory.VIEWER_SETTINGS, HelpCategory.EDITING, HelpCategory.SEARCH,
        HelpCategory.AI_FEATURES, HelpCategory.ALBUMS, HelpCategory.VAULT,
        HelpCategory.CLOUD_SYNC
    )
    fun getExploreMoreCategories() = listOf(
        HelpCategory.FAVORITES_TRASH, HelpCategory.LOCATIONS, HelpCategory.METADATA,
        HelpCategory.SETTINGS_APPEARANCE, HelpCategory.SETTINGS_GENERAL, HelpCategory.SETTINGS_NAVIGATION,
        HelpCategory.SETTINGS_SMART, HelpCategory.SETTINGS_SECURITY, HelpCategory.GESTURES,
        HelpCategory.SELECTION_ACTIONS, HelpCategory.ACCESSIBILITY
    )

    // region Get Started: Basics
    private val BASICS_TIPS = listOf(
        HelpTip(
            id = "basics_timeline", title = Res.string.help_tip_basics_timeline_title,
            subtitle = Res.string.help_tip_basics_timeline_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.GridView),
            category = HelpCategory.GET_STARTED_BASICS,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_basics_timeline_p1_title, description = Res.string.help_tip_basics_timeline_p1_desc, previewType = PreviewType.TIMELINE_GRID),
                TutorialPage(title = Res.string.help_tip_basics_timeline_p2_title, description = Res.string.help_tip_basics_timeline_p2_desc, steps = listOf(Res.string.help_tip_basics_timeline_p2_s1, Res.string.help_tip_basics_timeline_p2_s2, Res.string.help_tip_basics_timeline_p2_s3, Res.string.help_tip_basics_timeline_p2_s4, Res.string.help_tip_basics_timeline_p2_s5), previewType = PreviewType.TIMELINE_GRID)
            ), sinceVersion = "4.0.0",
            quickActions = listOf(QuickAction(Res.string.help_qa_open_timeline, Icons.Outlined.Collections, HelpRoute.Target.TIMELINE))
        ),
        HelpTip(
            id = "basics_view_media", title = Res.string.help_tip_basics_view_media_title,
            subtitle = Res.string.help_tip_basics_view_media_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Image),
            category = HelpCategory.GET_STARTED_BASICS,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_basics_view_media_p1_title, description = Res.string.help_tip_basics_view_media_p1_desc, previewType = PreviewType.MEDIA_VIEWER),
                TutorialPage(title = Res.string.help_tip_basics_view_media_p2_title, description = Res.string.help_tip_basics_view_media_p2_desc, previewType = PreviewType.MEDIA_VIEWER),
                TutorialPage(title = Res.string.help_tip_basics_view_media_p3_title, description = Res.string.help_tip_basics_view_media_p3_desc, previewType = PreviewType.MEDIA_VIEWER)
            ), sinceVersion = "4.0.0"
        ),
        HelpTip(
            id = "basics_select_media", title = Res.string.help_tip_basics_select_media_title,
            subtitle = Res.string.help_tip_basics_select_media_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.CheckCircle),
            category = HelpCategory.GET_STARTED_BASICS,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_basics_select_media_p1_title, description = Res.string.help_tip_basics_select_media_p1_desc, steps = listOf(Res.string.help_tip_basics_select_media_p1_s1, Res.string.help_tip_basics_select_media_p1_s2, Res.string.help_tip_basics_select_media_p1_s3, Res.string.help_tip_basics_select_media_p1_s4), previewType = PreviewType.TIMELINE_GRID),
                TutorialPage(title = Res.string.help_tip_basics_select_media_p2_title, description = Res.string.help_tip_basics_select_media_p2_desc)
            ), sinceVersion = "4.0.0"
        ),
        HelpTip(
            id = "basics_share", title = Res.string.help_tip_basics_share_title,
            subtitle = Res.string.help_tip_basics_share_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Share),
            category = HelpCategory.GET_STARTED_BASICS,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_basics_share_p1_title, description = Res.string.help_tip_basics_share_p1_desc, steps = listOf(Res.string.help_tip_basics_share_p1_s1, Res.string.help_tip_basics_share_p1_s2, Res.string.help_tip_basics_share_p1_s3, Res.string.help_tip_basics_share_p1_s4))
            ), sinceVersion = "4.0.0"
        ),
        HelpTip(
            id = "basics_copy_clipboard", title = Res.string.help_tip_basics_copy_clipboard_title,
            subtitle = Res.string.help_tip_basics_copy_clipboard_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.ContentCopy),
            category = HelpCategory.GET_STARTED_BASICS,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_basics_copy_clipboard_p1_title, description = Res.string.help_tip_basics_copy_clipboard_p1_desc, steps = listOf(Res.string.help_tip_basics_copy_clipboard_p1_s1, Res.string.help_tip_basics_copy_clipboard_p1_s2, Res.string.help_tip_basics_copy_clipboard_p1_s3, Res.string.help_tip_basics_copy_clipboard_p1_s4))
            ), sinceVersion = "4.1.1"
        ),
        HelpTip(id = "basics_setup", title = Res.string.help_tip_basics_setup_title, subtitle = Res.string.help_tip_basics_setup_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.RocketLaunch), category = HelpCategory.GET_STARTED_BASICS,
            deepLink = HelpRoute.Target.SETUP,
            pages = listOf(TutorialPage(title = Res.string.help_tip_basics_setup_p1_title, description = Res.string.help_tip_basics_setup_p1_desc)), sinceVersion = "5.1.0")
    )
    // endregion

    // region Get Started: Navigation
    private val NAVIGATION_TIPS = listOf(
        HelpTip(id = "nav_bottom_bar", title = Res.string.help_tip_nav_bottom_bar_title, subtitle = Res.string.help_tip_nav_bottom_bar_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.CallToAction), category = HelpCategory.GET_STARTED_NAVIGATION,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_nav_bottom_bar_p1_title, description = Res.string.help_tip_nav_bottom_bar_p1_desc, previewType = PreviewType.TIMELINE_GRID),
                TutorialPage(title = Res.string.help_tip_nav_bottom_bar_p2_title, description = Res.string.help_tip_nav_bottom_bar_p2_desc)
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "nav_albums", title = Res.string.help_tip_nav_albums_title, subtitle = Res.string.help_tip_nav_albums_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.PhotoAlbum), category = HelpCategory.GET_STARTED_NAVIGATION,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_nav_albums_p1_title, description = Res.string.help_tip_nav_albums_p1_desc, previewType = PreviewType.ALBUM_GRID),
                TutorialPage(title = Res.string.help_tip_nav_albums_p2_title, description = Res.string.help_tip_nav_albums_p2_desc, steps = listOf(Res.string.help_tip_nav_albums_p2_s1, Res.string.help_tip_nav_albums_p2_s2, Res.string.help_tip_nav_albums_p2_s3, Res.string.help_tip_nav_albums_p2_s4), previewType = PreviewType.ALBUM_GRID)
            ), sinceVersion = "4.0.0",
            quickActions = listOf(QuickAction(Res.string.help_qa_open_albums, Icons.Outlined.Collections, HelpRoute.Target.ALBUMS))),
        HelpTip(id = "nav_search", title = Res.string.help_tip_nav_search_title, subtitle = Res.string.help_tip_nav_search_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.ImageSearch), category = HelpCategory.GET_STARTED_NAVIGATION,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_nav_search_p1_title, description = Res.string.help_tip_nav_search_p1_desc, previewType = PreviewType.SEARCH_BAR),
                TutorialPage(title = Res.string.help_tip_nav_search_p2_title, description = Res.string.help_tip_nav_search_p2_desc, previewType = PreviewType.AI_SEARCH)
            ), sinceVersion = "4.0.0",
            quickActions = listOf(QuickAction(Res.string.help_qa_open_search, Icons.Outlined.ImageSearch, HelpRoute.Target.SEARCH))),
        HelpTip(id = "nav_old_navbar", title = Res.string.help_tip_nav_old_navbar_title, subtitle = Res.string.help_tip_nav_old_navbar_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.History), category = HelpCategory.GET_STARTED_NAVIGATION,
            deepLink = HelpRoute.Target.SETTINGS_NAVIGATION,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_nav_old_navbar_p1_title, description = Res.string.help_tip_nav_old_navbar_p1_desc, steps = listOf(Res.string.help_tip_nav_old_navbar_p1_s1, Res.string.help_tip_nav_old_navbar_p1_s2), previewType = PreviewType.NAV_BAR_PREVIEW)
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "nav_library", title = Res.string.help_tip_nav_library_title, subtitle = Res.string.help_tip_nav_library_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.PhotoLibrary), category = HelpCategory.GET_STARTED_NAVIGATION,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_nav_library_p1_title, description = Res.string.help_tip_nav_library_p1_desc)
            ), sinceVersion = "4.0.0",
            quickActions = listOf(QuickAction(Res.string.help_qa_open_library, Icons.Outlined.PhotoLibrary, HelpRoute.Target.LIBRARY))),
        HelpTip(id = "nav_launch_screen", title = Res.string.help_tip_nav_launch_screen_title, subtitle = Res.string.help_tip_nav_launch_screen_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Start), category = HelpCategory.GET_STARTED_NAVIGATION,
            deepLink = HelpRoute.Target.SETTINGS_NAVIGATION,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_nav_launch_screen_p1_title, description = Res.string.help_tip_nav_launch_screen_p1_desc, steps = listOf(Res.string.help_tip_nav_launch_screen_p1_s1, Res.string.help_tip_nav_launch_screen_p1_s2, Res.string.help_tip_nav_launch_screen_p1_s3))
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "nav_auto_hide_bars", title = Res.string.help_tip_nav_auto_hide_bars_title, subtitle = Res.string.help_tip_nav_auto_hide_bars_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.VisibilityOff), category = HelpCategory.GET_STARTED_NAVIGATION,
            deepLink = HelpRoute.Target.SETTINGS_NAVIGATION,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_nav_auto_hide_bars_p1_title, description = Res.string.help_tip_nav_auto_hide_bars_p1_desc, steps = listOf(Res.string.help_tip_nav_auto_hide_bars_p1_s1, Res.string.help_tip_nav_auto_hide_bars_p1_s2, Res.string.help_tip_nav_auto_hide_bars_p1_s3))
            ), sinceVersion = "4.0.0")
    )
    // endregion

    // region Get Started: Personalization
    private val PERSONALIZATION_TIPS = listOf(
        HelpTip(id = "personalize_theme", title = Res.string.help_tip_personalize_theme_title, subtitle = Res.string.help_tip_personalize_theme_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Palette), category = HelpCategory.GET_STARTED_PERSONALIZATION,
            deepLink = HelpRoute.Target.SETTINGS_APPEARANCE,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_personalize_theme_p1_title, description = Res.string.help_tip_personalize_theme_p1_desc, previewType = PreviewType.THEME_PICKER),
                TutorialPage(title = Res.string.help_tip_personalize_theme_p2_title, description = Res.string.help_tip_personalize_theme_p2_desc, steps = listOf(Res.string.help_tip_personalize_theme_p2_s1, Res.string.help_tip_personalize_theme_p2_s2, Res.string.help_tip_personalize_theme_p2_s3), previewType = PreviewType.THEME_PICKER)
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "personalize_colors", title = Res.string.help_tip_personalize_colors_title, subtitle = Res.string.help_tip_personalize_colors_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.ColorLens), category = HelpCategory.GET_STARTED_PERSONALIZATION,
            deepLink = HelpRoute.Target.COLOR_PALETTE,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_personalize_colors_p1_title, description = Res.string.help_tip_personalize_colors_p1_desc, previewType = PreviewType.COLOR_PALETTE),
                TutorialPage(title = Res.string.help_tip_personalize_colors_p2_title, description = Res.string.help_tip_personalize_colors_p2_desc, steps = listOf(Res.string.help_tip_personalize_colors_p2_s1, Res.string.help_tip_personalize_colors_p2_s2, Res.string.help_tip_personalize_colors_p2_s3, Res.string.help_tip_personalize_colors_p2_s4), previewType = PreviewType.COLOR_PALETTE)
            ), sinceVersion = "4.1.0"),
        HelpTip(id = "personalize_grid", title = Res.string.help_tip_personalize_grid_title, subtitle = Res.string.help_tip_personalize_grid_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.GridOn), category = HelpCategory.GET_STARTED_PERSONALIZATION,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_personalize_grid_p1_title, description = Res.string.help_tip_personalize_grid_p1_desc, previewType = PreviewType.PINCH_ZOOM_GRID),
                TutorialPage(title = Res.string.help_tip_personalize_grid_p2_title, description = Res.string.help_tip_personalize_grid_p2_desc, previewType = PreviewType.PINCH_ZOOM_GRID)
            ), sinceVersion = "4.0.1"),
        HelpTip(id = "personalize_amoled", title = Res.string.help_tip_personalize_amoled_title, subtitle = Res.string.help_tip_personalize_amoled_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.InvertColors), category = HelpCategory.GET_STARTED_PERSONALIZATION,
            deepLink = HelpRoute.Target.SETTINGS_APPEARANCE,
            pages = listOf(TutorialPage(title = Res.string.help_tip_personalize_amoled_p1_title, description = Res.string.help_tip_personalize_amoled_p1_desc, steps = listOf(Res.string.help_tip_personalize_amoled_p1_s1, Res.string.help_tip_personalize_amoled_p1_s2, Res.string.help_tip_personalize_amoled_p1_s3, Res.string.help_tip_personalize_amoled_p1_s4), previewType = PreviewType.THEME_PICKER)), sinceVersion = "4.0.0"),
        HelpTip(id = "personalize_dark_mode", title = Res.string.help_tip_personalize_dark_mode_title, subtitle = Res.string.help_tip_personalize_dark_mode_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.DarkMode), category = HelpCategory.GET_STARTED_PERSONALIZATION,
            deepLink = HelpRoute.Target.SETTINGS_APPEARANCE,
            pages = listOf(TutorialPage(title = Res.string.help_tip_personalize_dark_mode_p1_title, description = Res.string.help_tip_personalize_dark_mode_p1_desc, previewType = PreviewType.THEME_PICKER)), sinceVersion = "4.0.0"),
        HelpTip(id = "personalize_blur", title = Res.string.help_tip_personalize_blur_title, subtitle = Res.string.help_tip_personalize_blur_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.BlurOn), category = HelpCategory.GET_STARTED_PERSONALIZATION,
            deepLink = HelpRoute.Target.SETTINGS_APPEARANCE,
            pages = listOf(TutorialPage(title = Res.string.help_tip_personalize_blur_p1_title, description = Res.string.help_tip_personalize_blur_p1_desc, steps = listOf(Res.string.help_tip_personalize_blur_p1_s1, Res.string.help_tip_personalize_blur_p1_s2, Res.string.help_tip_personalize_blur_p1_s3))), sinceVersion = "4.0.0"),
        HelpTip(id = "personalize_shared_elements", title = Res.string.help_tip_personalize_shared_elements_title, subtitle = Res.string.help_tip_personalize_shared_elements_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Animation), category = HelpCategory.GET_STARTED_PERSONALIZATION,
            deepLink = HelpRoute.Target.COLOR_PALETTE,
            pages = listOf(TutorialPage(title = Res.string.help_tip_personalize_shared_elements_p1_title, description = Res.string.help_tip_personalize_shared_elements_p1_desc, steps = listOf(Res.string.help_tip_personalize_shared_elements_p1_s1, Res.string.help_tip_personalize_shared_elements_p1_s2, Res.string.help_tip_personalize_shared_elements_p1_s3))), sinceVersion = "4.0.0"),
        HelpTip(id = "personalize_app_name", title = Res.string.help_tip_personalize_app_name_title, subtitle = Res.string.help_tip_personalize_app_name_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.TextFields), category = HelpCategory.GET_STARTED_PERSONALIZATION,
            deepLink = HelpRoute.Target.SETTINGS_GENERAL,
            pages = listOf(TutorialPage(title = Res.string.help_tip_personalize_app_name_p1_title, description = Res.string.help_tip_personalize_app_name_p1_desc, steps = listOf(Res.string.help_tip_personalize_app_name_p1_s1, Res.string.help_tip_personalize_app_name_p1_s2, Res.string.help_tip_personalize_app_name_p1_s3, Res.string.help_tip_personalize_app_name_p1_s4))), sinceVersion = "4.1.1"),
        HelpTip(id = "personalize_app_logo", title = Res.string.help_tip_personalize_app_logo_title, subtitle = Res.string.help_tip_personalize_app_logo_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Apps), category = HelpCategory.GET_STARTED_PERSONALIZATION,
            deepLink = HelpRoute.Target.SETTINGS_GENERAL,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_personalize_app_logo_p1_title, description = Res.string.help_tip_personalize_app_logo_p1_desc, steps = listOf(Res.string.help_tip_personalize_app_logo_p1_s1, Res.string.help_tip_personalize_app_logo_p1_s2, Res.string.help_tip_personalize_app_logo_p1_s3, Res.string.help_tip_personalize_app_logo_p1_s4), previewType = PreviewType.THEME_PICKER)
            ), sinceVersion = "5.2.1"),
        HelpTip(id = "widget_styles", title = Res.string.help_tip_widget_styles_title, subtitle = Res.string.help_tip_widget_styles_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Widgets), category = HelpCategory.GET_STARTED_PERSONALIZATION,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_widget_styles_p1_title, description = Res.string.help_tip_widget_styles_p1_desc, steps = listOf(Res.string.help_tip_widget_styles_p1_s1, Res.string.help_tip_widget_styles_p1_s2, Res.string.help_tip_widget_styles_p1_s3, Res.string.help_tip_widget_styles_p1_s4))
            ), sinceVersion = "5.2.2")
    )
    // endregion

    // region Timeline & Albums
    private val TIMELINE_ALBUM_TIPS = listOf(
        HelpTip(id = "timeline_group_month", title = Res.string.help_tip_timeline_group_month_title, subtitle = Res.string.help_tip_timeline_group_month_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.CalendarViewMonth), category = HelpCategory.TIMELINE_ALBUMS,
            pages = listOf(TutorialPage(title = Res.string.help_tip_timeline_group_month_p1_title, description = Res.string.help_tip_timeline_group_month_p1_desc, previewType = PreviewType.TIMELINE_GRID)), sinceVersion = "4.0.0"),
        HelpTip(id = "timeline_layout_type", title = Res.string.help_tip_timeline_layout_type_title, subtitle = Res.string.help_tip_timeline_layout_type_subtitle,
            icon = HelpIcon.ofVector(Icons.AutoMirrored.Outlined.ViewQuilt), category = HelpCategory.TIMELINE_ALBUMS,
            deepLink = HelpRoute.Target.SETTINGS_TIMELINE_ALBUMS,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_timeline_layout_type_p1_title, description = Res.string.help_tip_timeline_layout_type_p1_desc, previewType = PreviewType.TIMELINE_GRID),
                TutorialPage(title = Res.string.help_tip_timeline_layout_type_p2_title, description = Res.string.help_tip_timeline_layout_type_p2_desc, steps = listOf(Res.string.help_tip_timeline_layout_type_p2_s1, Res.string.help_tip_timeline_layout_type_p2_s2, Res.string.help_tip_timeline_layout_type_p2_s3, Res.string.help_tip_timeline_layout_type_p2_s4), previewType = PreviewType.TIMELINE_GRID)
            ), sinceVersion = "4.2.0"),
        HelpTip(id = "timeline_group_similar", title = Res.string.help_tip_timeline_group_similar_title, subtitle = Res.string.help_tip_timeline_group_similar_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.BurstMode), category = HelpCategory.TIMELINE_ALBUMS,
            deepLink = HelpRoute.Target.SETTINGS_TIMELINE_ALBUMS,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_timeline_group_similar_p1_title, description = Res.string.help_tip_timeline_group_similar_p1_desc, previewType = PreviewType.TIMELINE_GRID),
                TutorialPage(title = Res.string.help_tip_timeline_group_similar_p2_title, description = Res.string.help_tip_timeline_group_similar_p2_desc, steps = listOf(Res.string.help_tip_timeline_group_similar_p2_s1, Res.string.help_tip_timeline_group_similar_p2_s2, Res.string.help_tip_timeline_group_similar_p2_s3, Res.string.help_tip_timeline_group_similar_p2_s4))
            ), sinceVersion = "4.1.2"),
        HelpTip(id = "timeline_gif_animation", title = Res.string.help_tip_timeline_gif_animation_title, subtitle = Res.string.help_tip_timeline_gif_animation_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.GifBox), category = HelpCategory.TIMELINE_ALBUMS,
            deepLink = HelpRoute.Target.SETTINGS_TIMELINE_ALBUMS,
            pages = listOf(TutorialPage(title = Res.string.help_tip_timeline_gif_animation_p1_title, description = Res.string.help_tip_timeline_gif_animation_p1_desc, steps = listOf(Res.string.help_tip_timeline_gif_animation_p1_s1, Res.string.help_tip_timeline_gif_animation_p1_s2, Res.string.help_tip_timeline_gif_animation_p1_s3))), sinceVersion = "4.1.2"),
        HelpTip(id = "albums_hide_timeline", title = Res.string.help_tip_albums_hide_timeline_title, subtitle = Res.string.help_tip_albums_hide_timeline_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.VisibilityOff), category = HelpCategory.TIMELINE_ALBUMS,
            deepLink = HelpRoute.Target.SETTINGS_TIMELINE_ALBUMS,
            pages = listOf(TutorialPage(title = Res.string.help_tip_albums_hide_timeline_p1_title, description = Res.string.help_tip_albums_hide_timeline_p1_desc, steps = listOf(Res.string.help_tip_albums_hide_timeline_p1_s1, Res.string.help_tip_albums_hide_timeline_p1_s2, Res.string.help_tip_albums_hide_timeline_p1_s3), previewType = PreviewType.ALBUM_GRID)), sinceVersion = "4.0.0"),
        HelpTip(id = "albums_merge_by_name", title = Res.string.help_tip_albums_merge_by_name_title, subtitle = Res.string.help_tip_albums_merge_by_name_subtitle,
            icon = HelpIcon.ofVector(Icons.AutoMirrored.Outlined.MergeType), category = HelpCategory.TIMELINE_ALBUMS,
            deepLink = HelpRoute.Target.SETTINGS_TIMELINE_ALBUMS,
            pages = listOf(TutorialPage(title = Res.string.help_tip_albums_merge_by_name_p1_title, description = Res.string.help_tip_albums_merge_by_name_p1_desc, steps = listOf(Res.string.help_tip_albums_merge_by_name_p1_s1, Res.string.help_tip_albums_merge_by_name_p1_s2, Res.string.help_tip_albums_merge_by_name_p1_s3), previewType = PreviewType.ALBUM_GRID)), sinceVersion = "4.2.0"),
        HelpTip(id = "timeline_modified_date", title = Res.string.help_tip_timeline_modified_date_title, subtitle = Res.string.help_tip_timeline_modified_date_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.EditCalendar), category = HelpCategory.TIMELINE_ALBUMS,
            deepLink = HelpRoute.Target.SETTINGS_TIMELINE_ALBUMS,
            pages = listOf(TutorialPage(title = Res.string.help_tip_timeline_modified_date_p1_title, description = Res.string.help_tip_timeline_modified_date_p1_desc, steps = listOf(Res.string.help_tip_timeline_modified_date_p1_s1, Res.string.help_tip_timeline_modified_date_p1_s2, Res.string.help_tip_timeline_modified_date_p1_s3))), sinceVersion = "5.1.4"),
        HelpTip(id = "timeline_capture_date", title = Res.string.help_tip_timeline_capture_date_title, subtitle = Res.string.help_tip_timeline_capture_date_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.PhotoCamera), category = HelpCategory.TIMELINE_ALBUMS,
            deepLink = HelpRoute.Target.SETTINGS_TIMELINE_ALBUMS,
            pages = listOf(TutorialPage(title = Res.string.help_tip_timeline_capture_date_p1_title, description = Res.string.help_tip_timeline_capture_date_p1_desc, steps = listOf(Res.string.help_tip_timeline_capture_date_p1_s1, Res.string.help_tip_timeline_capture_date_p1_s2, Res.string.help_tip_timeline_capture_date_p1_s3, Res.string.help_tip_timeline_capture_date_p1_s4))), sinceVersion = "5.2.0"),
        HelpTip(id = "timeline_date_header", title = Res.string.help_tip_timeline_date_header_title, subtitle = Res.string.help_tip_timeline_date_header_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Today), category = HelpCategory.TIMELINE_ALBUMS,
            deepLink = HelpRoute.Target.SETTINGS_TIMELINE_ALBUMS,
            pages = listOf(TutorialPage(title = Res.string.help_tip_timeline_date_header_p1_title, description = Res.string.help_tip_timeline_date_header_p1_desc, steps = listOf(Res.string.help_tip_timeline_date_header_p1_s1, Res.string.help_tip_timeline_date_header_p1_s2, Res.string.help_tip_timeline_date_header_p1_s3))), sinceVersion = "4.1.0"),
        HelpTip(id = "timeline_fav_icon", title = Res.string.help_tip_timeline_fav_icon_title, subtitle = Res.string.help_tip_timeline_fav_icon_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.FavoriteBorder), category = HelpCategory.TIMELINE_ALBUMS,
            deepLink = HelpRoute.Target.SETTINGS_TIMELINE_ALBUMS,
            pages = listOf(TutorialPage(title = Res.string.help_tip_timeline_fav_icon_p1_title, description = Res.string.help_tip_timeline_fav_icon_p1_desc, steps = listOf(Res.string.help_tip_timeline_fav_icon_p1_s1, Res.string.help_tip_timeline_fav_icon_p1_s2, Res.string.help_tip_timeline_fav_icon_p1_s3))), sinceVersion = "4.1.1"),
        HelpTip(id = "timeline_filter", title = Res.string.help_tip_timeline_filter_title, subtitle = Res.string.help_tip_timeline_filter_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.FilterList), category = HelpCategory.TIMELINE_ALBUMS,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_timeline_filter_p1_title, description = Res.string.help_tip_timeline_filter_p1_desc, previewType = PreviewType.TIMELINE_GRID),
                TutorialPage(title = Res.string.help_tip_timeline_filter_p2_title, description = Res.string.help_tip_timeline_filter_p2_desc, steps = listOf(Res.string.help_tip_timeline_filter_p2_s1, Res.string.help_tip_timeline_filter_p2_s2, Res.string.help_tip_timeline_filter_p2_s3, Res.string.help_tip_timeline_filter_p2_s4), previewType = PreviewType.TIMELINE_GRID)
            ), sinceVersion = "4.2.3"),
        HelpTip(id = "animated_formats", title = Res.string.help_tip_animated_formats_title, subtitle = Res.string.help_tip_animated_formats_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Animation), category = HelpCategory.TIMELINE_ALBUMS,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_animated_formats_p1_title, description = Res.string.help_tip_animated_formats_p1_desc, previewType = PreviewType.MEDIA_VIEWER),
                TutorialPage(title = Res.string.help_tip_animated_formats_p2_title, description = Res.string.help_tip_animated_formats_p2_desc)
            ), sinceVersion = "4.2.3"),
        HelpTip(id = "story_cards", title = Res.string.help_tip_story_cards_title, subtitle = Res.string.help_tip_story_cards_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.AutoStories), category = HelpCategory.TIMELINE_ALBUMS,
            deepLink = HelpRoute.Target.STORY_CARDS_SETTINGS,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_story_cards_p1_title, description = Res.string.help_tip_story_cards_p1_desc, previewType = PreviewType.TIMELINE_GRID),
                TutorialPage(title = Res.string.help_tip_story_cards_p2_title, description = Res.string.help_tip_story_cards_p2_desc, steps = listOf(Res.string.help_tip_story_cards_p2_s1, Res.string.help_tip_story_cards_p2_s2, Res.string.help_tip_story_cards_p2_s3, Res.string.help_tip_story_cards_p2_s4))
            ), sinceVersion = "4.3.0"),
        HelpTip(id = "timeline_group_period", title = Res.string.help_tip_timeline_group_period_title, subtitle = Res.string.help_tip_timeline_group_period_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.CalendarMonth), category = HelpCategory.TIMELINE_ALBUMS,
            deepLink = HelpRoute.Target.SETTINGS_TIMELINE_ALBUMS,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_timeline_group_period_p1_title, description = Res.string.help_tip_timeline_group_period_p1_desc, previewType = PreviewType.TIMELINE_GRID),
                TutorialPage(title = Res.string.help_tip_timeline_group_period_p2_title, description = Res.string.help_tip_timeline_group_period_p2_desc, steps = listOf(Res.string.help_tip_timeline_group_period_p2_s1, Res.string.help_tip_timeline_group_period_p2_s2, Res.string.help_tip_timeline_group_period_p2_s3, Res.string.help_tip_timeline_group_period_p2_s4))
            ), sinceVersion = "5.0.0"),
        HelpTip(id = "media_type_albums", title = Res.string.help_tip_media_type_albums_title, subtitle = Res.string.help_tip_media_type_albums_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.PermMedia), category = HelpCategory.TIMELINE_ALBUMS,
            deepLink = HelpRoute.Target.SETTINGS_TIMELINE_ALBUMS,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_media_type_albums_p1_title, description = Res.string.help_tip_media_type_albums_p1_desc, steps = listOf(Res.string.help_tip_media_type_albums_p1_s1, Res.string.help_tip_media_type_albums_p1_s2, Res.string.help_tip_media_type_albums_p1_s3), previewType = PreviewType.ALBUM_GRID)
            ), sinceVersion = "5.1.0"),
        HelpTip(id = "library_sections_edit", title = Res.string.help_tip_library_sections_edit_title, subtitle = Res.string.help_tip_library_sections_edit_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.DashboardCustomize), category = HelpCategory.TIMELINE_ALBUMS,
            deepLink = HelpRoute.Target.LIBRARY,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_library_sections_edit_p1_title, description = Res.string.help_tip_library_sections_edit_p1_desc, steps = listOf(Res.string.help_tip_library_sections_edit_p1_s1, Res.string.help_tip_library_sections_edit_p1_s2, Res.string.help_tip_library_sections_edit_p1_s3, Res.string.help_tip_library_sections_edit_p1_s4))
            ), sinceVersion = "5.2.2")
    )
    // endregion

    // region Viewing
    private val VIEWING_TIPS = listOf(
        HelpTip(id = "view_zoom", title = Res.string.help_tip_view_zoom_title, subtitle = Res.string.help_tip_view_zoom_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.ZoomIn), category = HelpCategory.VIEWING,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_view_zoom_p1_title, description = Res.string.help_tip_view_zoom_p1_desc, previewType = PreviewType.MEDIA_VIEWER),
                TutorialPage(title = Res.string.help_tip_view_zoom_p2_title, description = Res.string.help_tip_view_zoom_p2_desc, previewType = PreviewType.MEDIA_VIEWER)
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "view_video", title = Res.string.help_tip_view_video_title, subtitle = Res.string.help_tip_view_video_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.PlayCircle), category = HelpCategory.VIEWING,
            deepLink = HelpRoute.Target.SETTINGS_MEDIA_VIEWER,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_view_video_p1_title, description = Res.string.help_tip_view_video_p1_desc, previewType = PreviewType.MEDIA_VIEWER),
                TutorialPage(title = Res.string.help_tip_view_video_p2_title, description = Res.string.help_tip_view_video_p2_desc, steps = listOf(Res.string.help_tip_view_video_p2_s1, Res.string.help_tip_view_video_p2_s2, Res.string.help_tip_view_video_p2_s3, Res.string.help_tip_view_video_p2_s4))
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "view_details", title = Res.string.help_tip_view_details_title, subtitle = Res.string.help_tip_view_details_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Info), category = HelpCategory.VIEWING,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_view_details_p1_title, description = Res.string.help_tip_view_details_p1_desc, previewType = PreviewType.EXIF_VIEWER),
                TutorialPage(title = Res.string.help_tip_view_details_p2_title, description = Res.string.help_tip_view_details_p2_desc, previewType = PreviewType.EXIF_VIEWER)
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "view_swipe", title = Res.string.help_tip_view_swipe_title, subtitle = Res.string.help_tip_view_swipe_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Swipe), category = HelpCategory.VIEWING,
            pages = listOf(TutorialPage(title = Res.string.help_tip_view_swipe_p1_title, description = Res.string.help_tip_view_swipe_p1_desc, previewType = PreviewType.MEDIA_VIEWER)), sinceVersion = "4.0.0"),
        HelpTip(id = "view_tap_navigation", title = Res.string.help_tip_view_tap_navigation_title, subtitle = Res.string.help_tip_view_tap_navigation_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.TouchApp), category = HelpCategory.VIEWING,
            deepLink = HelpRoute.Target.SETTINGS_MEDIA_VIEWER,
            pages = listOf(TutorialPage(title = Res.string.help_tip_view_tap_navigation_p1_title, description = Res.string.help_tip_view_tap_navigation_p1_desc, steps = listOf(Res.string.help_tip_view_tap_navigation_p1_s1, Res.string.help_tip_view_tap_navigation_p1_s2, Res.string.help_tip_view_tap_navigation_p1_s3, Res.string.help_tip_view_tap_navigation_p1_s4), previewType = PreviewType.MEDIA_VIEWER)), sinceVersion = "5.1.3"),
        HelpTip(id = "view_panorama", title = Res.string.help_tip_view_panorama_title, subtitle = Res.string.help_tip_view_panorama_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Panorama), category = HelpCategory.VIEWING,
            pages = listOf(TutorialPage(title = Res.string.help_tip_view_panorama_p1_title, description = Res.string.help_tip_view_panorama_p1_desc, previewType = PreviewType.PANORAMA)), sinceVersion = "4.1.0"),
        HelpTip(id = "view_motion_photo", title = Res.string.help_tip_view_motion_photo_title, subtitle = Res.string.help_tip_view_motion_photo_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.MotionPhotosOn), category = HelpCategory.VIEWING,
            pages = listOf(TutorialPage(title = Res.string.help_tip_view_motion_photo_p1_title, description = Res.string.help_tip_view_motion_photo_p1_desc, previewType = PreviewType.MOTION_PHOTO)), sinceVersion = "4.1.0"),
        HelpTip(id = "motion_photo_frames", title = Res.string.help_tip_motion_photo_frames_title, subtitle = Res.string.help_tip_motion_photo_frames_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.BurstMode), category = HelpCategory.VIEWING,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_motion_photo_frames_p1_title, description = Res.string.help_tip_motion_photo_frames_p1_desc, steps = listOf(Res.string.help_tip_motion_photo_frames_p1_s1, Res.string.help_tip_motion_photo_frames_p1_s2, Res.string.help_tip_motion_photo_frames_p1_s3, Res.string.help_tip_motion_photo_frames_p1_s4), previewType = PreviewType.MOTION_PHOTO)
            ), sinceVersion = "5.1.1"),
        HelpTip(id = "view_full_brightness", title = Res.string.help_tip_view_full_brightness_title, subtitle = Res.string.help_tip_view_full_brightness_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.BrightnessHigh), category = HelpCategory.VIEWING,
            deepLink = HelpRoute.Target.SETTINGS_MEDIA_VIEWER,
            pages = listOf(TutorialPage(title = Res.string.help_tip_view_full_brightness_p1_title, description = Res.string.help_tip_view_full_brightness_p1_desc, steps = listOf(Res.string.help_tip_view_full_brightness_p1_s1, Res.string.help_tip_view_full_brightness_p1_s2, Res.string.help_tip_view_full_brightness_p1_s3))), sinceVersion = "4.0.0"),
        HelpTip(id = "view_group_members", title = Res.string.help_tip_view_group_members_title, subtitle = Res.string.help_tip_view_group_members_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.ViewCarousel), category = HelpCategory.VIEWING,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_view_group_members_p1_title, description = Res.string.help_tip_view_group_members_p1_desc, previewType = PreviewType.MEDIA_VIEWER),
                TutorialPage(title = Res.string.help_tip_view_group_members_p2_title, description = Res.string.help_tip_view_group_members_p2_desc, previewType = PreviewType.MEDIA_VIEWER)
            ), sinceVersion = "4.1.2"),
        HelpTip(id = "view_lock_image", title = Res.string.help_tip_view_lock_image_title, subtitle = Res.string.help_tip_view_lock_image_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Lock), category = HelpCategory.VIEWING,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_view_lock_image_p1_title, description = Res.string.help_tip_view_lock_image_p1_desc, previewType = PreviewType.MEDIA_VIEWER),
                TutorialPage(title = Res.string.help_tip_view_lock_image_p2_title, description = Res.string.help_tip_view_lock_image_p2_desc, steps = listOf(Res.string.help_tip_view_lock_image_p2_s1, Res.string.help_tip_view_lock_image_p2_s2, Res.string.help_tip_view_lock_image_p2_s3, Res.string.help_tip_view_lock_image_p2_s4), previewType = PreviewType.MEDIA_VIEWER)
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "view_video_subtitles", title = Res.string.help_tip_video_subtitles_title, subtitle = Res.string.help_tip_video_subtitles_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Subtitles), category = HelpCategory.VIEWING,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_video_subtitles_p1_title, description = Res.string.help_tip_video_subtitles_p1_desc, previewType = PreviewType.MEDIA_VIEWER),
                TutorialPage(title = Res.string.help_tip_video_subtitles_p2_title, description = Res.string.help_tip_video_subtitles_p2_desc, steps = listOf(Res.string.help_tip_video_subtitles_p2_s1, Res.string.help_tip_video_subtitles_p2_s2, Res.string.help_tip_video_subtitles_p2_s3), previewType = PreviewType.MEDIA_VIEWER)
            ), sinceVersion = "4.2.3"),
        HelpTip(id = "view_video_zoom", title = Res.string.help_tip_video_zoom_title, subtitle = Res.string.help_tip_video_zoom_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.ZoomOutMap), category = HelpCategory.VIEWING,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_video_zoom_p1_title, description = Res.string.help_tip_video_zoom_p1_desc, previewType = PreviewType.MEDIA_VIEWER)
            ), sinceVersion = "4.2.3"),
        HelpTip(id = "view_cutout", title = Res.string.help_tip_view_cutout_title, subtitle = Res.string.help_tip_view_cutout_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.ContentCut), category = HelpCategory.VIEWING,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_view_cutout_p1_title, description = Res.string.help_tip_view_cutout_p1_desc, steps = listOf(Res.string.help_tip_view_cutout_p1_s1, Res.string.help_tip_view_cutout_p1_s2, Res.string.help_tip_view_cutout_p1_s3, Res.string.help_tip_view_cutout_p1_s4), previewType = PreviewType.SUBJECT_CUTOUT)
            ), sinceVersion = "5.1.0"),
        HelpTip(id = "view_codes", title = Res.string.help_tip_view_codes_title, subtitle = Res.string.help_tip_view_codes_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.QrCodeScanner), category = HelpCategory.VIEWING,
            deepLink = HelpRoute.Target.SETTINGS_MEDIA_VIEWER,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_view_codes_p1_title, description = Res.string.help_tip_view_codes_p1_desc, steps = listOf(Res.string.help_tip_view_codes_p1_s1, Res.string.help_tip_view_codes_p1_s2, Res.string.help_tip_view_codes_p1_s3), previewType = PreviewType.CODE_SCAN),
                TutorialPage(title = Res.string.help_tip_view_codes_p2_title, description = Res.string.help_tip_view_codes_p2_desc, steps = listOf(Res.string.help_tip_view_codes_p2_s1, Res.string.help_tip_view_codes_p2_s2, Res.string.help_tip_view_codes_p2_s3))
            ), sinceVersion = "6.0.0"),
        HelpTip(id = "slideshow_play", title = Res.string.help_tip_slideshow_play_title, subtitle = Res.string.help_tip_slideshow_play_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Slideshow), category = HelpCategory.VIEWING,
            deepLink = HelpRoute.Target.SLIDESHOW_SETTINGS,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_slideshow_play_p1_title, description = Res.string.help_tip_slideshow_play_p1_desc, steps = listOf(Res.string.help_tip_slideshow_play_p1_s1, Res.string.help_tip_slideshow_play_p1_s2, Res.string.help_tip_slideshow_play_p1_s3), previewType = PreviewType.SLIDESHOW)
            ), sinceVersion = "5.1.0"),
        HelpTip(id = "view_hdr_video", title = Res.string.help_tip_view_hdr_video_title, subtitle = Res.string.help_tip_view_hdr_video_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.HdrOn), category = HelpCategory.VIEWING,
            deepLink = HelpRoute.Target.SETTINGS_MEDIA_VIEWER,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_view_hdr_video_p1_title, description = Res.string.help_tip_view_hdr_video_p1_desc, steps = listOf(Res.string.help_tip_view_hdr_video_p1_s1, Res.string.help_tip_view_hdr_video_p1_s2, Res.string.help_tip_view_hdr_video_p1_s3, Res.string.help_tip_view_hdr_video_p1_s4), previewType = PreviewType.VIDEO_CONTROLS)
            ), sinceVersion = "5.2.2")
    )
    // endregion

    // region Viewer Actions & Settings
    private val VIEWER_ACTION_TIPS = listOf(
        HelpTip(id = "action_share", title = Res.string.help_tip_action_share_title, subtitle = Res.string.help_tip_action_share_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Share), category = HelpCategory.VIEWER_ACTIONS,
            pages = listOf(TutorialPage(title = Res.string.help_tip_action_share_p1_title, description = Res.string.help_tip_action_share_p1_desc, steps = listOf(Res.string.help_tip_action_share_p1_s1, Res.string.help_tip_action_share_p1_s2, Res.string.help_tip_action_share_p1_s3))), sinceVersion = "4.0.0"),
        HelpTip(id = "action_edit", title = Res.string.help_tip_action_edit_title, subtitle = Res.string.help_tip_action_edit_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Tune), category = HelpCategory.VIEWER_ACTIONS,
            pages = listOf(TutorialPage(title = Res.string.help_tip_action_edit_p1_title, description = Res.string.help_tip_action_edit_p1_desc, steps = listOf(Res.string.help_tip_action_edit_p1_s1, Res.string.help_tip_action_edit_p1_s2, Res.string.help_tip_action_edit_p1_s3, Res.string.help_tip_action_edit_p1_s4))), sinceVersion = "4.0.0"),
        HelpTip(id = "action_hide_vault", title = Res.string.help_tip_action_hide_vault_title, subtitle = Res.string.help_tip_action_hide_vault_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Lock), category = HelpCategory.VIEWER_ACTIONS,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_action_hide_vault_p1_title, description = Res.string.help_tip_action_hide_vault_p1_desc, previewType = PreviewType.VAULT_LOCK),
                TutorialPage(title = Res.string.help_tip_action_hide_vault_p2_title, description = Res.string.help_tip_action_hide_vault_p2_desc, steps = listOf(Res.string.help_tip_action_hide_vault_p2_s1, Res.string.help_tip_action_hide_vault_p2_s2, Res.string.help_tip_action_hide_vault_p2_s3, Res.string.help_tip_action_hide_vault_p2_s4), previewType = PreviewType.VAULT_LOCK)
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "action_copy_to_album", title = Res.string.help_tip_action_copy_to_album_title, subtitle = Res.string.help_tip_action_copy_to_album_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.ContentCopy), category = HelpCategory.VIEWER_ACTIONS,
            pages = listOf(TutorialPage(title = Res.string.help_tip_action_copy_to_album_p1_title, description = Res.string.help_tip_action_copy_to_album_p1_desc, steps = listOf(Res.string.help_tip_action_copy_to_album_p1_s1, Res.string.help_tip_action_copy_to_album_p1_s2, Res.string.help_tip_action_copy_to_album_p1_s3, Res.string.help_tip_action_copy_to_album_p1_s4))), sinceVersion = "4.0.0"),
        HelpTip(id = "action_move_to_album", title = Res.string.help_tip_action_move_to_album_title, subtitle = Res.string.help_tip_action_move_to_album_subtitle,
            icon = HelpIcon.ofVector(Icons.AutoMirrored.Outlined.DriveFileMove), category = HelpCategory.VIEWER_ACTIONS,
            pages = listOf(TutorialPage(title = Res.string.help_tip_action_move_to_album_p1_title, description = Res.string.help_tip_action_move_to_album_p1_desc, steps = listOf(Res.string.help_tip_action_move_to_album_p1_s1, Res.string.help_tip_action_move_to_album_p1_s2, Res.string.help_tip_action_move_to_album_p1_s3, Res.string.help_tip_action_move_to_album_p1_s4, Res.string.help_tip_action_move_to_album_p1_s5))), sinceVersion = "5.1.3"),
        HelpTip(id = "action_add_collection", title = Res.string.help_tip_action_add_collection_title, subtitle = Res.string.help_tip_action_add_collection_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.CollectionsBookmark), category = HelpCategory.VIEWER_ACTIONS,
            pages = listOf(TutorialPage(title = Res.string.help_tip_action_add_collection_p1_title, description = Res.string.help_tip_action_add_collection_p1_desc, previewType = PreviewType.COLLECTION_VIEW)), sinceVersion = "4.2.0"),
        HelpTip(id = "action_view_all_metadata", title = Res.string.help_tip_action_view_all_metadata_title, subtitle = Res.string.help_tip_action_view_all_metadata_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Info), category = HelpCategory.VIEWER_ACTIONS,
            pages = listOf(TutorialPage(title = Res.string.help_tip_action_view_all_metadata_p1_title, description = Res.string.help_tip_action_view_all_metadata_p1_desc, previewType = PreviewType.EXIF_VIEWER)), sinceVersion = "4.1.3"),
        HelpTip(id = "action_cast", title = Res.string.help_tip_viewer_cast_title, subtitle = Res.string.help_tip_viewer_cast_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Cast), category = HelpCategory.VIEWER_ACTIONS,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_viewer_cast_p1_title, description = Res.string.help_tip_viewer_cast_p1_desc, previewType = PreviewType.CASTING),
                TutorialPage(title = Res.string.help_tip_viewer_cast_p2_title, description = Res.string.help_tip_viewer_cast_p2_desc, steps = listOf(Res.string.help_tip_viewer_cast_p2_s1, Res.string.help_tip_viewer_cast_p2_s2, Res.string.help_tip_viewer_cast_p2_s3, Res.string.help_tip_viewer_cast_p2_s4), previewType = PreviewType.CASTING)
            ), sinceVersion = "4.2.1"),
        HelpTip(id = "action_save_motion_video", title = Res.string.help_tip_action_save_motion_video_title, subtitle = Res.string.help_tip_action_save_motion_video_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.VideoFile), category = HelpCategory.VIEWER_ACTIONS,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_action_save_motion_video_p1_title, description = Res.string.help_tip_action_save_motion_video_p1_desc, steps = listOf(Res.string.help_tip_action_save_motion_video_p1_s1, Res.string.help_tip_action_save_motion_video_p1_s2, Res.string.help_tip_action_save_motion_video_p1_s3, Res.string.help_tip_action_save_motion_video_p1_s4), previewType = PreviewType.MEDIA_VIEWER)
            ), sinceVersion = "5.0.0"),
        HelpTip(id = "action_rename", title = Res.string.help_tip_action_rename_title, subtitle = Res.string.help_tip_action_rename_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.DriveFileRenameOutline), category = HelpCategory.VIEWER_ACTIONS,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_action_rename_p1_title, description = Res.string.help_tip_action_rename_p1_desc, steps = listOf(Res.string.help_tip_action_rename_p1_s1, Res.string.help_tip_action_rename_p1_s2, Res.string.help_tip_action_rename_p1_s3, Res.string.help_tip_action_rename_p1_s4), previewType = PreviewType.MEDIA_VIEWER)
            ), sinceVersion = "5.2.1"),
        HelpTip(id = "action_visual_search", title = Res.string.help_tip_action_visual_search_title, subtitle = Res.string.help_tip_action_visual_search_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.ImageSearch), category = HelpCategory.VIEWER_ACTIONS,
            deepLink = HelpRoute.Target.SETTINGS_MEDIA_VIEWER,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_action_visual_search_p1_title, description = Res.string.help_tip_action_visual_search_p1_desc, previewType = PreviewType.MEDIA_VIEWER),
                TutorialPage(title = Res.string.help_tip_action_visual_search_p2_title, description = Res.string.help_tip_action_visual_search_p2_desc, steps = listOf(Res.string.help_tip_action_visual_search_p2_s1, Res.string.help_tip_action_visual_search_p2_s2, Res.string.help_tip_action_visual_search_p2_s3, Res.string.help_tip_action_visual_search_p2_s4))
            ), sinceVersion = "5.2.1")
    )

    private val VIEWER_SETTINGS_TIPS = listOf(
        HelpTip(id = "viewer_default_editor", title = Res.string.help_tip_viewer_default_editor_title, subtitle = Res.string.help_tip_viewer_default_editor_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Edit), category = HelpCategory.VIEWER_SETTINGS,
            deepLink = HelpRoute.Target.SETTINGS_MEDIA_VIEWER,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_viewer_default_editor_p1_title, description = Res.string.help_tip_viewer_default_editor_p1_desc, steps = listOf(Res.string.help_tip_viewer_default_editor_p1_s1, Res.string.help_tip_viewer_default_editor_p1_s2, Res.string.help_tip_viewer_default_editor_p1_s3)),
                TutorialPage(title = Res.string.help_tip_viewer_default_editor_p2_title, description = Res.string.help_tip_viewer_default_editor_p2_desc)
            ), sinceVersion = "4.1.2"),
        HelpTip(id = "viewer_auto_play", title = Res.string.help_tip_viewer_auto_play_title, subtitle = Res.string.help_tip_viewer_auto_play_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.PlayCircle), category = HelpCategory.VIEWER_SETTINGS,
            deepLink = HelpRoute.Target.SETTINGS_MEDIA_VIEWER,
            pages = listOf(TutorialPage(title = Res.string.help_tip_viewer_auto_play_p1_title, description = Res.string.help_tip_viewer_auto_play_p1_desc, steps = listOf(Res.string.help_tip_viewer_auto_play_p1_s1, Res.string.help_tip_viewer_auto_play_p1_s2))), sinceVersion = "4.0.0"),
        HelpTip(id = "viewer_auto_hide_video", title = Res.string.help_tip_viewer_auto_hide_video_title, subtitle = Res.string.help_tip_viewer_auto_hide_video_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.VisibilityOff), category = HelpCategory.VIEWER_SETTINGS,
            deepLink = HelpRoute.Target.SETTINGS_MEDIA_VIEWER,
            pages = listOf(TutorialPage(title = Res.string.help_tip_viewer_auto_hide_video_p1_title, description = Res.string.help_tip_viewer_auto_hide_video_p1_desc, steps = listOf(Res.string.help_tip_viewer_auto_hide_video_p1_s1, Res.string.help_tip_viewer_auto_hide_video_p1_s2, Res.string.help_tip_viewer_auto_hide_video_p1_s3))), sinceVersion = "4.0.0"),
        HelpTip(id = "viewer_date_header", title = Res.string.help_tip_viewer_date_header_title, subtitle = Res.string.help_tip_viewer_date_header_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Today), category = HelpCategory.VIEWER_SETTINGS,
            deepLink = HelpRoute.Target.SETTINGS_MEDIA_VIEWER,
            pages = listOf(TutorialPage(title = Res.string.help_tip_viewer_date_header_p1_title, description = Res.string.help_tip_viewer_date_header_p1_desc, steps = listOf(Res.string.help_tip_viewer_date_header_p1_s1, Res.string.help_tip_viewer_date_header_p1_s2, Res.string.help_tip_viewer_date_header_p1_s3))), sinceVersion = "4.1.0"),
        HelpTip(id = "viewer_fav_button", title = Res.string.help_tip_viewer_fav_button_title, subtitle = Res.string.help_tip_viewer_fav_button_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.FavoriteBorder), category = HelpCategory.VIEWER_SETTINGS,
            deepLink = HelpRoute.Target.SETTINGS_MEDIA_VIEWER,
            pages = listOf(TutorialPage(title = Res.string.help_tip_viewer_fav_button_p1_title, description = Res.string.help_tip_viewer_fav_button_p1_desc, steps = listOf(Res.string.help_tip_viewer_fav_button_p1_s1, Res.string.help_tip_viewer_fav_button_p1_s2, Res.string.help_tip_viewer_fav_button_p1_s3))), sinceVersion = "4.0.0"),
        HelpTip(id = "viewer_auto_contrast", title = Res.string.help_tip_viewer_auto_contrast_title, subtitle = Res.string.help_tip_viewer_auto_contrast_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Contrast), category = HelpCategory.VIEWER_SETTINGS,
            deepLink = HelpRoute.Target.SETTINGS_APPEARANCE,
            pages = listOf(TutorialPage(title = Res.string.help_tip_viewer_auto_contrast_p1_title, description = Res.string.help_tip_viewer_auto_contrast_p1_desc, steps = listOf(Res.string.help_tip_viewer_auto_contrast_p1_s1, Res.string.help_tip_viewer_auto_contrast_p1_s2, Res.string.help_tip_viewer_auto_contrast_p1_s3))), sinceVersion = "4.2.1"),
        HelpTip(id = "video_options_popup", title = Res.string.help_tip_video_options_popup_title, subtitle = Res.string.help_tip_video_options_popup_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.VideoSettings), category = HelpCategory.VIEWER_SETTINGS,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_video_options_popup_p1_title, description = Res.string.help_tip_video_options_popup_p1_desc, previewType = PreviewType.VIDEO_CONTROLS)
            ), sinceVersion = "4.3.0"),
        HelpTip(id = "video_fix_black", title = Res.string.help_tip_video_fix_black_title, subtitle = Res.string.help_tip_video_fix_black_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Movie), category = HelpCategory.VIEWER_SETTINGS,
            deepLink = HelpRoute.Target.SETTINGS_MEDIA_VIEWER,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_video_fix_black_p1_title, description = Res.string.help_tip_video_fix_black_p1_desc)
            ), sinceVersion = "5.0.3"),
        HelpTip(id = "viewer_dark_background", title = Res.string.help_tip_viewer_dark_background_title, subtitle = Res.string.help_tip_viewer_dark_background_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.DarkMode), category = HelpCategory.VIEWER_SETTINGS,
            deepLink = HelpRoute.Target.SETTINGS_MEDIA_VIEWER,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_viewer_dark_background_p1_title, description = Res.string.help_tip_viewer_dark_background_p1_desc, steps = listOf(Res.string.help_tip_viewer_dark_background_p1_s1, Res.string.help_tip_viewer_dark_background_p1_s2), previewType = PreviewType.MEDIA_VIEWER)
            ), sinceVersion = "5.2.1")
    )
    // endregion

    // region Editing
    private val EDITING_TIPS = listOf(
        HelpTip(id = "edit_crop", title = Res.string.help_tip_edit_crop_title, subtitle = Res.string.help_tip_edit_crop_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Crop), category = HelpCategory.EDITING,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_edit_crop_p1_title, description = Res.string.help_tip_edit_crop_p1_desc, previewType = PreviewType.PHOTO_EDITOR_CROP),
                TutorialPage(title = Res.string.help_tip_edit_crop_p2_title, description = Res.string.help_tip_edit_crop_p2_desc, previewType = PreviewType.PHOTO_EDITOR_CROP),
                TutorialPage(title = Res.string.help_tip_edit_crop_p3_title, description = Res.string.help_tip_edit_crop_p3_desc, steps = listOf(Res.string.help_tip_edit_crop_p3_s1, Res.string.help_tip_edit_crop_p3_s2, Res.string.help_tip_edit_crop_p3_s3, Res.string.help_tip_edit_crop_p3_s4, Res.string.help_tip_edit_crop_p3_s5), previewType = PreviewType.PHOTO_EDITOR_CROP)
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "edit_rotate", title = Res.string.help_tip_edit_rotate_title, subtitle = Res.string.help_tip_edit_rotate_subtitle,
            icon = HelpIcon.ofVector(Icons.AutoMirrored.Outlined.RotateRight), category = HelpCategory.EDITING,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_edit_rotate_p1_title, description = Res.string.help_tip_edit_rotate_p1_desc, previewType = PreviewType.PHOTO_EDITOR_CROP),
                TutorialPage(title = Res.string.help_tip_edit_rotate_p2_title, description = Res.string.help_tip_edit_rotate_p2_desc, steps = listOf(Res.string.help_tip_edit_rotate_p2_s1, Res.string.help_tip_edit_rotate_p2_s2, Res.string.help_tip_edit_rotate_p2_s3, Res.string.help_tip_edit_rotate_p2_s4))
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "edit_filters", title = Res.string.help_tip_edit_filters_title, subtitle = Res.string.help_tip_edit_filters_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.PhotoFilter), category = HelpCategory.EDITING,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_edit_filters_p1_title, description = Res.string.help_tip_edit_filters_p1_desc, previewType = PreviewType.PHOTO_EDITOR_FILTERS),
                TutorialPage(title = Res.string.help_tip_edit_filters_p2_title, description = Res.string.help_tip_edit_filters_p2_desc, steps = listOf(Res.string.help_tip_edit_filters_p2_s1, Res.string.help_tip_edit_filters_p2_s2, Res.string.help_tip_edit_filters_p2_s3, Res.string.help_tip_edit_filters_p2_s4, Res.string.help_tip_edit_filters_p2_s5), previewType = PreviewType.PHOTO_EDITOR_FILTERS)
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "edit_effects", title = Res.string.help_tip_edit_effects_title, subtitle = Res.string.help_tip_edit_effects_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.AutoFixHigh), category = HelpCategory.EDITING,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_edit_effects_p1_title, description = Res.string.help_tip_edit_effects_p1_desc, previewType = PreviewType.PHOTO_EDITOR_FILTERS),
                TutorialPage(title = Res.string.help_tip_edit_effects_p2_title, description = Res.string.help_tip_edit_effects_p2_desc, steps = listOf(Res.string.help_tip_edit_effects_p2_s1, Res.string.help_tip_edit_effects_p2_s2, Res.string.help_tip_edit_effects_p2_s3, Res.string.help_tip_edit_effects_p2_s4), previewType = PreviewType.PHOTO_EDITOR_FILTERS)
            ), sinceVersion = "5.0.0"),
        HelpTip(id = "edit_adjustments", title = Res.string.help_tip_edit_adjustments_title, subtitle = Res.string.help_tip_edit_adjustments_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Tune), category = HelpCategory.EDITING,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_edit_adjustments_p1_title, description = Res.string.help_tip_edit_adjustments_p1_desc, previewType = PreviewType.PHOTO_EDITOR_FILTERS),
                TutorialPage(title = Res.string.help_tip_edit_adjustments_p2_title, description = Res.string.help_tip_edit_adjustments_p2_desc, previewType = PreviewType.PHOTO_EDITOR_FILTERS)
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "edit_markup", title = Res.string.help_tip_edit_markup_title, subtitle = Res.string.help_tip_edit_markup_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Draw), category = HelpCategory.EDITING,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_edit_markup_p1_title, description = Res.string.help_tip_edit_markup_p1_desc, previewType = PreviewType.PHOTO_EDITOR_MARKUP),
                TutorialPage(title = Res.string.help_tip_edit_markup_p2_title, description = Res.string.help_tip_edit_markup_p2_desc, previewType = PreviewType.PHOTO_EDITOR_MARKUP),
                TutorialPage(title = Res.string.help_tip_edit_markup_p3_title, description = Res.string.help_tip_edit_markup_p3_desc, steps = listOf(Res.string.help_tip_edit_markup_p3_s1, Res.string.help_tip_edit_markup_p3_s2, Res.string.help_tip_edit_markup_p3_s3, Res.string.help_tip_edit_markup_p3_s4, Res.string.help_tip_edit_markup_p3_s5, Res.string.help_tip_edit_markup_p3_s6), previewType = PreviewType.PHOTO_EDITOR_MARKUP)
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "edit_save", title = Res.string.help_tip_edit_save_title, subtitle = Res.string.help_tip_edit_save_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Save), category = HelpCategory.EDITING,
            deepLink = HelpRoute.Target.EDIT_BACKUPS_VIEWER,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_edit_save_p1_title, description = Res.string.help_tip_edit_save_p1_desc),
                TutorialPage(title = Res.string.help_tip_edit_save_p2_title, description = Res.string.help_tip_edit_save_p2_desc, steps = listOf(Res.string.help_tip_edit_save_p2_s1, Res.string.help_tip_edit_save_p2_s2, Res.string.help_tip_edit_save_p2_s3, Res.string.help_tip_edit_save_p2_s4))
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "edit_backups", title = Res.string.help_tip_edit_backups_title, subtitle = Res.string.help_tip_edit_backups_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.SettingsBackupRestore), category = HelpCategory.EDITING,
            deepLink = HelpRoute.Target.EDIT_BACKUPS_VIEWER,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_edit_backups_p1_title, description = Res.string.help_tip_edit_backups_p1_desc),
                TutorialPage(title = Res.string.help_tip_edit_backups_p2_title, description = Res.string.help_tip_edit_backups_p2_desc, steps = listOf(Res.string.help_tip_edit_backups_p2_s1, Res.string.help_tip_edit_backups_p2_s2, Res.string.help_tip_edit_backups_p2_s3, Res.string.help_tip_edit_backups_p2_s4))
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "raw_develop", title = Res.string.help_tip_raw_develop_title, subtitle = Res.string.help_tip_raw_develop_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.RawOn), category = HelpCategory.EDITING,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_raw_develop_p1_title, description = Res.string.help_tip_raw_develop_p1_desc, steps = listOf(Res.string.help_tip_raw_develop_p1_s1, Res.string.help_tip_raw_develop_p1_s2, Res.string.help_tip_raw_develop_p1_s3, Res.string.help_tip_raw_develop_p1_s4), previewType = PreviewType.PHOTO_EDITOR_FILTERS)
            ), sinceVersion = "5.1.0")
    )
    // endregion

    // region Search
    private val SEARCH_TIPS = listOf(
        HelpTip(id = "search_text", title = Res.string.help_tip_search_text_title, subtitle = Res.string.help_tip_search_text_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Search), category = HelpCategory.SEARCH,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_search_text_p1_title, description = Res.string.help_tip_search_text_p1_desc, previewType = PreviewType.SEARCH_BAR),
                TutorialPage(title = Res.string.help_tip_search_text_p2_title, description = Res.string.help_tip_search_text_p2_desc, previewType = PreviewType.SEARCH_BAR)
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "search_ai", title = Res.string.help_tip_search_ai_title, subtitle = Res.string.help_tip_search_ai_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.ImageSearch), category = HelpCategory.SEARCH,
            deepLink = HelpRoute.Target.AI_MODELS_MANAGER,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_search_ai_p1_title, description = Res.string.help_tip_search_ai_p1_desc, previewType = PreviewType.AI_SEARCH),
                TutorialPage(title = Res.string.help_tip_search_ai_p2_title, description = Res.string.help_tip_search_ai_p2_desc, previewType = PreviewType.AI_SEARCH),
                TutorialPage(title = Res.string.help_tip_search_ai_p3_title, description = Res.string.help_tip_search_ai_p3_desc, steps = listOf(Res.string.help_tip_search_ai_p3_s1, Res.string.help_tip_search_ai_p3_s2, Res.string.help_tip_search_ai_p3_s3, Res.string.help_tip_search_ai_p3_s4), actionLabel = Res.string.help_action_open_settings, previewType = PreviewType.AI_SEARCH)
            ), sinceVersion = "4.1.0"),
        HelpTip(id = "search_ignored_albums", title = Res.string.help_tip_search_ignored_albums_title, subtitle = Res.string.help_tip_search_ignored_albums_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.SearchOff), category = HelpCategory.SEARCH,
            deepLink = HelpRoute.Target.SETTINGS_SMART_FEATURES,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_search_ignored_albums_p1_title, description = Res.string.help_tip_search_ignored_albums_p1_desc, steps = listOf(Res.string.help_tip_search_ignored_albums_p1_s1, Res.string.help_tip_search_ignored_albums_p1_s2, Res.string.help_tip_search_ignored_albums_p1_s3, Res.string.help_tip_search_ignored_albums_p1_s4), previewType = PreviewType.AI_SEARCH)
            ), sinceVersion = "5.1.1"),
        HelpTip(id = "search_history", title = Res.string.help_tip_search_history_title, subtitle = Res.string.help_tip_search_history_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.History), category = HelpCategory.SEARCH,
            pages = listOf(TutorialPage(title = Res.string.help_tip_search_history_p1_title, description = Res.string.help_tip_search_history_p1_desc, previewType = PreviewType.SEARCH_BAR)), sinceVersion = "4.0.0"),
        HelpTip(id = "search_indexing", title = Res.string.help_tip_search_indexing_title, subtitle = Res.string.help_tip_search_indexing_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Update), category = HelpCategory.SEARCH,
            pages = listOf(TutorialPage(title = Res.string.help_tip_search_indexing_p1_title, description = Res.string.help_tip_search_indexing_p1_desc)), sinceVersion = "4.1.0"),
        HelpTip(id = "search_metadata", title = Res.string.help_tip_search_metadata_title, subtitle = Res.string.help_tip_search_metadata_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Description), category = HelpCategory.SEARCH,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_search_metadata_p1_title, description = Res.string.help_tip_search_metadata_p1_desc, previewType = PreviewType.SEARCH_BAR)
            ), sinceVersion = "4.2.3")
    )
    // endregion

    // region AI Features
    private val AI_TIPS = listOf(
        HelpTip(id = "ai_categories", title = Res.string.help_tip_ai_categories_title, subtitle = Res.string.help_tip_ai_categories_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Category), category = HelpCategory.AI_FEATURES,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_ai_categories_p1_title, description = Res.string.help_tip_ai_categories_p1_desc, previewType = PreviewType.AI_CATEGORIES),
                TutorialPage(title = Res.string.help_tip_ai_categories_p2_title, description = Res.string.help_tip_ai_categories_p2_desc, steps = listOf(Res.string.help_tip_ai_categories_p2_s1, Res.string.help_tip_ai_categories_p2_s2, Res.string.help_tip_ai_categories_p2_s3, Res.string.help_tip_ai_categories_p2_s4), previewType = PreviewType.AI_CATEGORIES),
                TutorialPage(title = Res.string.help_tip_ai_categories_p3_title, description = Res.string.help_tip_ai_categories_p3_desc, previewType = PreviewType.AI_CATEGORIES)
            ), sinceVersion = "4.1.0",
            quickActions = listOf(QuickAction(Res.string.help_qa_open_categories, Icons.Outlined.AutoAwesome, HelpRoute.Target.CATEGORIES))),
        HelpTip(id = "ai_create_category", title = Res.string.help_tip_ai_create_category_title, subtitle = Res.string.help_tip_ai_create_category_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.CreateNewFolder), category = HelpCategory.AI_FEATURES,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_ai_create_category_p1_title, description = Res.string.help_tip_ai_create_category_p1_desc, previewType = PreviewType.AI_CATEGORIES),
                TutorialPage(title = Res.string.help_tip_ai_create_category_p2_title, description = Res.string.help_tip_ai_create_category_p2_desc, steps = listOf(Res.string.help_tip_ai_create_category_p2_s1, Res.string.help_tip_ai_create_category_p2_s2, Res.string.help_tip_ai_create_category_p2_s3, Res.string.help_tip_ai_create_category_p2_s4, Res.string.help_tip_ai_create_category_p2_s5), previewType = PreviewType.AI_CATEGORIES)
            ), sinceVersion = "4.1.0"),
        HelpTip(id = "ai_models", title = Res.string.help_tip_ai_models_title, subtitle = Res.string.help_tip_ai_models_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.CloudDownload), category = HelpCategory.AI_FEATURES,
            deepLink = HelpRoute.Target.AI_MODELS_MANAGER,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_ai_models_p1_title, description = Res.string.help_tip_ai_models_p1_desc),
                TutorialPage(title = Res.string.help_tip_ai_models_p2_title, description = Res.string.help_tip_ai_models_p2_desc, steps = listOf(Res.string.help_tip_ai_models_p2_s1, Res.string.help_tip_ai_models_p2_s2, Res.string.help_tip_ai_models_p2_s3, Res.string.help_tip_ai_models_p2_s4), actionLabel = Res.string.help_action_open_settings)
            ), sinceVersion = "4.1.0"),
        HelpTip(id = "ai_indexing", title = Res.string.help_tip_ai_indexing_title, subtitle = Res.string.help_tip_ai_indexing_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.AutoAwesome), category = HelpCategory.AI_FEATURES,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_ai_indexing_p1_title, description = Res.string.help_tip_ai_indexing_p1_desc),
                TutorialPage(title = Res.string.help_tip_ai_indexing_p2_title, description = Res.string.help_tip_ai_indexing_p2_desc)
            ), sinceVersion = "4.1.0"),
        HelpTip(id = "people_grouping", title = Res.string.help_tip_people_grouping_title, subtitle = Res.string.help_tip_people_grouping_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Groups), category = HelpCategory.AI_FEATURES,
            deepLink = HelpRoute.Target.PEOPLE_LIST,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_people_grouping_p1_title, description = Res.string.help_tip_people_grouping_p1_desc, steps = listOf(Res.string.help_tip_people_grouping_p1_s1, Res.string.help_tip_people_grouping_p1_s2, Res.string.help_tip_people_grouping_p1_s3, Res.string.help_tip_people_grouping_p1_s4), previewType = PreviewType.AI_CATEGORIES)
            ), sinceVersion = "5.1.0"),
        HelpTip(id = "people_fix_faces", title = Res.string.help_tip_people_fix_faces_title, subtitle = Res.string.help_tip_people_fix_faces_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Face), category = HelpCategory.AI_FEATURES,
            deepLink = HelpRoute.Target.PEOPLE_LIST,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_people_fix_faces_p1_title, description = Res.string.help_tip_people_fix_faces_p1_desc, steps = listOf(Res.string.help_tip_people_fix_faces_p1_s1, Res.string.help_tip_people_fix_faces_p1_s2, Res.string.help_tip_people_fix_faces_p1_s3, Res.string.help_tip_people_fix_faces_p1_s4), previewType = PreviewType.AI_CATEGORIES)
            ), sinceVersion = "5.2.0"),
        HelpTip(id = "people_manage", title = Res.string.help_tip_people_manage_title, subtitle = Res.string.help_tip_people_manage_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.ManageAccounts), category = HelpCategory.AI_FEATURES,
            deepLink = HelpRoute.Target.PEOPLE_LIST,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_people_manage_p1_title, description = Res.string.help_tip_people_manage_p1_desc, steps = listOf(Res.string.help_tip_people_manage_p1_s1, Res.string.help_tip_people_manage_p1_s2, Res.string.help_tip_people_manage_p1_s3, Res.string.help_tip_people_manage_p1_s4), previewType = PreviewType.AI_CATEGORIES)
            ), sinceVersion = "5.2.2")
    )
    // endregion

    // region Albums & Collections
    private val ALBUM_TIPS = listOf(
        HelpTip(id = "albums_browse", title = Res.string.help_tip_albums_browse_title, subtitle = Res.string.help_tip_albums_browse_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.PhotoAlbum), category = HelpCategory.ALBUMS,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_albums_browse_p1_title, description = Res.string.help_tip_albums_browse_p1_desc, previewType = PreviewType.ALBUM_GRID),
                TutorialPage(title = Res.string.help_tip_albums_browse_p2_title, description = Res.string.help_tip_albums_browse_p2_desc, previewType = PreviewType.ALBUM_GRID)
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "albums_pin", title = Res.string.help_tip_albums_pin_title, subtitle = Res.string.help_tip_albums_pin_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.PushPin), category = HelpCategory.ALBUMS,
            pages = listOf(TutorialPage(title = Res.string.help_tip_albums_pin_p1_title, description = Res.string.help_tip_albums_pin_p1_desc, steps = listOf(Res.string.help_tip_albums_pin_p1_s1, Res.string.help_tip_albums_pin_p1_s2, Res.string.help_tip_albums_pin_p1_s3, Res.string.help_tip_albums_pin_p1_s4), previewType = PreviewType.ALBUM_GRID)), sinceVersion = "4.0.0"),
        HelpTip(id = "albums_ignore", title = Res.string.help_tip_albums_ignore_title, subtitle = Res.string.help_tip_albums_ignore_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.VisibilityOff), category = HelpCategory.ALBUMS,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_albums_ignore_p1_title, description = Res.string.help_tip_albums_ignore_p1_desc, steps = listOf(Res.string.help_tip_albums_ignore_p1_s1, Res.string.help_tip_albums_ignore_p1_s2, Res.string.help_tip_albums_ignore_p1_s3)),
                TutorialPage(title = Res.string.help_tip_albums_ignore_p2_title, description = Res.string.help_tip_albums_ignore_p2_desc)
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "albums_groups", title = Res.string.help_tip_albums_groups_title, subtitle = Res.string.help_tip_albums_groups_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Workspaces), category = HelpCategory.ALBUMS,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_albums_groups_p1_title, description = Res.string.help_tip_albums_groups_p1_desc, previewType = PreviewType.ALBUM_GRID),
                TutorialPage(title = Res.string.help_tip_albums_groups_p2_title, description = Res.string.help_tip_albums_groups_p2_desc, steps = listOf(Res.string.help_tip_albums_groups_p2_s1, Res.string.help_tip_albums_groups_p2_s2, Res.string.help_tip_albums_groups_p2_s3, Res.string.help_tip_albums_groups_p2_s4, Res.string.help_tip_albums_groups_p2_s5), previewType = PreviewType.ALBUM_GRID)
            ), sinceVersion = "4.2.0"),
        HelpTip(id = "albums_sections", title = Res.string.help_tip_albums_sections_title, subtitle = Res.string.help_tip_albums_sections_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.ViewAgenda), category = HelpCategory.ALBUMS,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_albums_sections_p1_title, description = Res.string.help_tip_albums_sections_p1_desc, previewType = PreviewType.ALBUM_GRID),
                TutorialPage(title = Res.string.help_tip_albums_sections_p2_title, description = Res.string.help_tip_albums_sections_p2_desc, steps = listOf(Res.string.help_tip_albums_sections_p2_s1, Res.string.help_tip_albums_sections_p2_s2, Res.string.help_tip_albums_sections_p2_s3, Res.string.help_tip_albums_sections_p2_s4), previewType = PreviewType.ALBUM_GRID)
            ), sinceVersion = "5.0.0"),
        HelpTip(id = "albums_collections", title = Res.string.help_tip_albums_collections_title, subtitle = Res.string.help_tip_albums_collections_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.CollectionsBookmark), category = HelpCategory.ALBUMS,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_albums_collections_p1_title, description = Res.string.help_tip_albums_collections_p1_desc, previewType = PreviewType.COLLECTION_VIEW),
                TutorialPage(title = Res.string.help_tip_albums_collections_p2_title, description = Res.string.help_tip_albums_collections_p2_desc, steps = listOf(Res.string.help_tip_albums_collections_p2_s1, Res.string.help_tip_albums_collections_p2_s2, Res.string.help_tip_albums_collections_p2_s3, Res.string.help_tip_albums_collections_p2_s4), previewType = PreviewType.COLLECTION_VIEW)
            ), sinceVersion = "4.2.0"),
        HelpTip(id = "albums_thumbnails", title = Res.string.help_tip_albums_thumbnails_title, subtitle = Res.string.help_tip_albums_thumbnails_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Image), category = HelpCategory.ALBUMS,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_albums_thumbnails_p1_title, description = Res.string.help_tip_albums_thumbnails_p1_desc, previewType = PreviewType.ALBUM_GRID),
                TutorialPage(title = Res.string.help_tip_albums_thumbnails_p2_title, description = Res.string.help_tip_albums_thumbnails_p2_desc, steps = listOf(Res.string.help_tip_albums_thumbnails_p2_s1, Res.string.help_tip_albums_thumbnails_p2_s2, Res.string.help_tip_albums_thumbnails_p2_s3, Res.string.help_tip_albums_thumbnails_p2_s4), previewType = PreviewType.ALBUM_GRID)
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "albums_picker_search", title = Res.string.help_tip_album_picker_search_title, subtitle = Res.string.help_tip_album_picker_search_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Search), category = HelpCategory.ALBUMS,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_album_picker_search_p1_title, description = Res.string.help_tip_album_picker_search_p1_desc)
            ), sinceVersion = "4.2.3"),
        HelpTip(id = "albums_ignored_manage", title = Res.string.help_tip_albums_ignored_manage_title, subtitle = Res.string.help_tip_albums_ignored_manage_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Block), category = HelpCategory.ALBUMS,
            deepLink = HelpRoute.Target.IGNORED,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_albums_ignored_manage_p1_title, description = Res.string.help_tip_albums_ignored_manage_p1_desc, steps = listOf(Res.string.help_tip_albums_ignored_manage_p1_s1, Res.string.help_tip_albums_ignored_manage_p1_s2, Res.string.help_tip_albums_ignored_manage_p1_s3), previewType = PreviewType.ALBUM_GRID)
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "album_cover", title = Res.string.help_tip_album_cover_title, subtitle = Res.string.help_tip_album_cover_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.AddPhotoAlternate), category = HelpCategory.ALBUMS,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_album_cover_p1_title, description = Res.string.help_tip_album_cover_p1_desc, steps = listOf(Res.string.help_tip_album_cover_p1_s1, Res.string.help_tip_album_cover_p1_s2, Res.string.help_tip_album_cover_p1_s3), previewType = PreviewType.ALBUM_GRID)
            ), sinceVersion = "5.1.0")
    )
    // endregion

    // region Vault
    private val VAULT_TIPS = listOf(
        HelpTip(id = "vault_setup", title = Res.string.help_tip_vault_setup_title, subtitle = Res.string.help_tip_vault_setup_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.EnhancedEncryption), category = HelpCategory.VAULT,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_vault_setup_p1_title, description = Res.string.help_tip_vault_setup_p1_desc, previewType = PreviewType.VAULT_LOCK),
                TutorialPage(title = Res.string.help_tip_vault_setup_p2_title, description = Res.string.help_tip_vault_setup_p2_desc, steps = listOf(Res.string.help_tip_vault_setup_p2_s1, Res.string.help_tip_vault_setup_p2_s2, Res.string.help_tip_vault_setup_p2_s3, Res.string.help_tip_vault_setup_p2_s4), previewType = PreviewType.VAULT_LOCK),
                TutorialPage(title = Res.string.help_tip_vault_setup_p3_title, description = Res.string.help_tip_vault_setup_p3_desc, previewType = PreviewType.VAULT_LOCK)
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "vault_add", title = Res.string.help_tip_vault_add_title, subtitle = Res.string.help_tip_vault_add_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Lock), category = HelpCategory.VAULT,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_vault_add_p1_title, description = Res.string.help_tip_vault_add_p1_desc, previewType = PreviewType.VAULT_LOCK),
                TutorialPage(title = Res.string.help_tip_vault_add_p2_title, description = Res.string.help_tip_vault_add_p2_desc, steps = listOf(Res.string.help_tip_vault_add_p2_s1, Res.string.help_tip_vault_add_p2_s2, Res.string.help_tip_vault_add_p2_s3, Res.string.help_tip_vault_add_p2_s4), previewType = PreviewType.VAULT_LOCK)
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "vault_restore", title = Res.string.help_tip_vault_restore_title, subtitle = Res.string.help_tip_vault_restore_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Restore), category = HelpCategory.VAULT,
            pages = listOf(TutorialPage(title = Res.string.help_tip_vault_restore_p1_title, description = Res.string.help_tip_vault_restore_p1_desc, previewType = PreviewType.VAULT_LOCK)), sinceVersion = "4.0.0"),
        HelpTip(id = "vault_video_streaming", title = Res.string.help_tip_vault_video_streaming_title, subtitle = Res.string.help_tip_vault_video_streaming_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.OndemandVideo), category = HelpCategory.VAULT,
            pages = listOf(TutorialPage(title = Res.string.help_tip_vault_video_streaming_p1_title, description = Res.string.help_tip_vault_video_streaming_p1_desc)), sinceVersion = "4.0.0"),
        HelpTip(id = "vault_overhaul", title = Res.string.help_tip_vault_overhaul_title, subtitle = Res.string.help_tip_vault_overhaul_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Shield), category = HelpCategory.VAULT,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_vault_overhaul_p1_title, description = Res.string.help_tip_vault_overhaul_p1_desc, steps = listOf(Res.string.help_tip_vault_overhaul_p1_s1, Res.string.help_tip_vault_overhaul_p1_s2, Res.string.help_tip_vault_overhaul_p1_s3), previewType = PreviewType.VAULT_LOCK),
                TutorialPage(title = Res.string.help_tip_vault_overhaul_p2_title, description = Res.string.help_tip_vault_overhaul_p2_desc, steps = listOf(Res.string.help_tip_vault_overhaul_p2_s1, Res.string.help_tip_vault_overhaul_p2_s2, Res.string.help_tip_vault_overhaul_p2_s3), previewType = PreviewType.VAULT_LOCK)
            ), sinceVersion = "4.2.1",
            quickActions = listOf(QuickAction(Res.string.help_qa_open_vault, Icons.Outlined.Lock, HelpRoute.Target.VAULT))),
        HelpTip(id = "vault_one_lock", title = Res.string.help_tip_vault_one_lock_title, subtitle = Res.string.help_tip_vault_one_lock_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.LockOpen), category = HelpCategory.VAULT,
            deepLink = HelpRoute.Target.VAULT,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_vault_one_lock_p1_title, description = Res.string.help_tip_vault_one_lock_p1_desc, steps = listOf(Res.string.help_tip_vault_one_lock_p1_s1, Res.string.help_tip_vault_one_lock_p1_s2, Res.string.help_tip_vault_one_lock_p1_s3), previewType = PreviewType.VAULT_LOCK)
            ), sinceVersion = "5.2.1")
    )
    // endregion

    // region Cloud & Sync
    private val CLOUD_TIPS = listOf(
        HelpTip(id = "cloud_connect", title = Res.string.help_tip_cloud_connect_title, subtitle = Res.string.help_tip_cloud_connect_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Cloud), category = HelpCategory.CLOUD_SYNC,
            deepLink = HelpRoute.Target.CLOUD_ACCOUNTS,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_cloud_connect_p1_title, description = Res.string.help_tip_cloud_connect_p1_desc),
                TutorialPage(title = Res.string.help_tip_cloud_connect_p2_title, description = Res.string.help_tip_cloud_connect_p2_desc, steps = listOf(Res.string.help_tip_cloud_connect_p2_s1, Res.string.help_tip_cloud_connect_p2_s2, Res.string.help_tip_cloud_connect_p2_s3, Res.string.help_tip_cloud_connect_p2_s4))
            ), sinceVersion = "5.0.0"),
        HelpTip(id = "cloud_nextcloud_login", title = Res.string.help_tip_cloud_nextcloud_login_title, subtitle = Res.string.help_tip_cloud_nextcloud_login_subtitle,
            icon = HelpIcon.ofVector(Icons.AutoMirrored.Outlined.Login), category = HelpCategory.CLOUD_SYNC,
            deepLink = HelpRoute.Target.CLOUD_ACCOUNTS,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_cloud_nextcloud_login_p1_title, description = Res.string.help_tip_cloud_nextcloud_login_p1_desc, steps = listOf(Res.string.help_tip_cloud_nextcloud_login_p1_s1, Res.string.help_tip_cloud_nextcloud_login_p1_s2, Res.string.help_tip_cloud_nextcloud_login_p1_s3, Res.string.help_tip_cloud_nextcloud_login_p1_s4))
            ), sinceVersion = "5.1.1"),
        HelpTip(id = "cloud_timeline", title = Res.string.help_tip_cloud_timeline_title, subtitle = Res.string.help_tip_cloud_timeline_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.CloudDone), category = HelpCategory.CLOUD_SYNC,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_cloud_timeline_p1_title, description = Res.string.help_tip_cloud_timeline_p1_desc, previewType = PreviewType.TIMELINE_GRID),
                TutorialPage(title = Res.string.help_tip_cloud_timeline_p2_title, description = Res.string.help_tip_cloud_timeline_p2_desc, previewType = PreviewType.MEDIA_VIEWER)
            ), sinceVersion = "5.0.0"),
        HelpTip(id = "cloud_backup", title = Res.string.help_tip_cloud_backup_title, subtitle = Res.string.help_tip_cloud_backup_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Backup), category = HelpCategory.CLOUD_SYNC,
            deepLink = HelpRoute.Target.CLOUD_BACKUP_DASHBOARD,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_cloud_backup_p1_title, description = Res.string.help_tip_cloud_backup_p1_desc),
                TutorialPage(title = Res.string.help_tip_cloud_backup_p2_title, description = Res.string.help_tip_cloud_backup_p2_desc, steps = listOf(Res.string.help_tip_cloud_backup_p2_s1, Res.string.help_tip_cloud_backup_p2_s2, Res.string.help_tip_cloud_backup_p2_s3, Res.string.help_tip_cloud_backup_p2_s4))
            ), sinceVersion = "5.0.0"),
        HelpTip(id = "cloud_explore", title = Res.string.help_tip_cloud_explore_title, subtitle = Res.string.help_tip_cloud_explore_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Explore), category = HelpCategory.CLOUD_SYNC,
            deepLink = HelpRoute.Target.CLOUD_BACKUP_DASHBOARD,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_cloud_explore_p1_title, description = Res.string.help_tip_cloud_explore_p1_desc),
                TutorialPage(title = Res.string.help_tip_cloud_explore_p2_title, description = Res.string.help_tip_cloud_explore_p2_desc)
            ), sinceVersion = "5.0.0"),
        HelpTip(id = "cloud_add_server", title = Res.string.help_tip_cloud_add_server_title, subtitle = Res.string.help_tip_cloud_add_server_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Dns), category = HelpCategory.CLOUD_SYNC,
            deepLink = HelpRoute.Target.CLOUD_ACCOUNTS,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_cloud_add_server_p1_title, description = Res.string.help_tip_cloud_add_server_p1_desc, steps = listOf(Res.string.help_tip_cloud_add_server_p1_s1, Res.string.help_tip_cloud_add_server_p1_s2, Res.string.help_tip_cloud_add_server_p1_s3, Res.string.help_tip_cloud_add_server_p1_s4))
            ), sinceVersion = "5.1.0"),
        HelpTip(id = "cloud_destinations", title = Res.string.help_tip_cloud_destinations_title, subtitle = Res.string.help_tip_cloud_destinations_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.CloudUpload), category = HelpCategory.CLOUD_SYNC,
            deepLink = HelpRoute.Target.CLOUD_DESTINATIONS,
            pages = listOf(TutorialPage(title = Res.string.help_tip_cloud_destinations_p1_title, description = Res.string.help_tip_cloud_destinations_p1_desc)), sinceVersion = "5.1.0"),
        HelpTip(id = "cloud_provider_settings", title = Res.string.help_tip_cloud_provider_settings_title, subtitle = Res.string.help_tip_cloud_provider_settings_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Settings), category = HelpCategory.CLOUD_SYNC,
            deepLink = HelpRoute.Target.CLOUD_BACKUP_DASHBOARD,
            pages = listOf(TutorialPage(title = Res.string.help_tip_cloud_provider_settings_p1_title, description = Res.string.help_tip_cloud_provider_settings_p1_desc)), sinceVersion = "5.1.0"),
        HelpTip(id = "cloud_shared_links", title = Res.string.help_tip_cloud_shared_links_title, subtitle = Res.string.help_tip_cloud_shared_links_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Link), category = HelpCategory.CLOUD_SYNC,
            deepLink = HelpRoute.Target.SHARED_LINKS,
            pages = listOf(TutorialPage(title = Res.string.help_tip_cloud_shared_links_p1_title, description = Res.string.help_tip_cloud_shared_links_p1_desc)), sinceVersion = "5.1.0"),
        HelpTip(id = "cloud_sync_status", title = Res.string.help_tip_cloud_sync_status_title, subtitle = Res.string.help_tip_cloud_sync_status_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.CloudSync), category = HelpCategory.CLOUD_SYNC,
            deepLink = HelpRoute.Target.CLOUD_BACKUP_DASHBOARD,
            pages = listOf(TutorialPage(title = Res.string.help_tip_cloud_sync_status_p1_title, description = Res.string.help_tip_cloud_sync_status_p1_desc)), sinceVersion = "5.1.0"),
        HelpTip(id = "cloud_people", title = Res.string.help_tip_cloud_people_title, subtitle = Res.string.help_tip_cloud_people_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Groups), category = HelpCategory.CLOUD_SYNC,
            deepLink = HelpRoute.Target.PEOPLE_LIST,
            pages = listOf(TutorialPage(title = Res.string.help_tip_cloud_people_p1_title, description = Res.string.help_tip_cloud_people_p1_desc)), sinceVersion = "5.1.0"),
        HelpTip(id = "cloud_free_up_space", title = Res.string.help_tip_cloud_free_up_space_title, subtitle = Res.string.help_tip_cloud_free_up_space_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Storage), category = HelpCategory.CLOUD_SYNC,
            deepLink = HelpRoute.Target.FREE_UP_SPACE,
            pages = listOf(TutorialPage(title = Res.string.help_tip_cloud_free_up_space_p1_title, description = Res.string.help_tip_cloud_free_up_space_p1_desc)), sinceVersion = "5.1.0"),
        HelpTip(id = "cloud_two_way_sync", title = Res.string.help_tip_cloud_two_way_sync_title, subtitle = Res.string.help_tip_cloud_two_way_sync_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Sync), category = HelpCategory.CLOUD_SYNC,
            deepLink = HelpRoute.Target.SYNC_STATUS,
            pages = listOf(TutorialPage(title = Res.string.help_tip_cloud_two_way_sync_p1_title, description = Res.string.help_tip_cloud_two_way_sync_p1_desc, steps = listOf(Res.string.help_tip_cloud_two_way_sync_p1_s1, Res.string.help_tip_cloud_two_way_sync_p1_s2, Res.string.help_tip_cloud_two_way_sync_p1_s3))), sinceVersion = "5.2.0"),
        HelpTip(id = "cloud_smart_search", title = Res.string.help_tip_cloud_smart_search_title, subtitle = Res.string.help_tip_cloud_smart_search_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.ImageSearch), category = HelpCategory.CLOUD_SYNC,
            deepLink = HelpRoute.Target.SETTINGS_CLOUD_INDEXING,
            pages = listOf(TutorialPage(title = Res.string.help_tip_cloud_smart_search_p1_title, description = Res.string.help_tip_cloud_smart_search_p1_desc, steps = listOf(Res.string.help_tip_cloud_smart_search_p1_s1, Res.string.help_tip_cloud_smart_search_p1_s2, Res.string.help_tip_cloud_smart_search_p1_s3, Res.string.help_tip_cloud_smart_search_p1_s4))), sinceVersion = "5.2.0"),
        HelpTip(id = "cloud_upload_paths", title = Res.string.help_tip_cloud_upload_paths_title, subtitle = Res.string.help_tip_cloud_upload_paths_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.FolderOpen), category = HelpCategory.CLOUD_SYNC,
            deepLink = HelpRoute.Target.CLOUD_BACKUP_DASHBOARD,
            pages = listOf(TutorialPage(title = Res.string.help_tip_cloud_upload_paths_p1_title, description = Res.string.help_tip_cloud_upload_paths_p1_desc, steps = listOf(Res.string.help_tip_cloud_upload_paths_p1_s1, Res.string.help_tip_cloud_upload_paths_p1_s2, Res.string.help_tip_cloud_upload_paths_p1_s3, Res.string.help_tip_cloud_upload_paths_p1_s4))), sinceVersion = "5.2.0"),
        HelpTip(id = "cloud_transfer", title = Res.string.help_tip_cloud_transfer_title, subtitle = Res.string.help_tip_cloud_transfer_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.SwapHoriz), category = HelpCategory.CLOUD_SYNC,
            pages = listOf(TutorialPage(title = Res.string.help_tip_cloud_transfer_p1_title, description = Res.string.help_tip_cloud_transfer_p1_desc, steps = listOf(Res.string.help_tip_cloud_transfer_p1_s1, Res.string.help_tip_cloud_transfer_p1_s2, Res.string.help_tip_cloud_transfer_p1_s3, Res.string.help_tip_cloud_transfer_p1_s4))), sinceVersion = "5.2.0"),
        HelpTip(id = "cloud_free_up_space_auto", title = Res.string.help_tip_cloud_free_up_space_auto_title, subtitle = Res.string.help_tip_cloud_free_up_space_auto_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Schedule), category = HelpCategory.CLOUD_SYNC,
            deepLink = HelpRoute.Target.FREE_UP_SPACE,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_cloud_free_up_space_auto_p1_title, description = Res.string.help_tip_cloud_free_up_space_auto_p1_desc, steps = listOf(Res.string.help_tip_cloud_free_up_space_auto_p1_s1, Res.string.help_tip_cloud_free_up_space_auto_p1_s2, Res.string.help_tip_cloud_free_up_space_auto_p1_s3, Res.string.help_tip_cloud_free_up_space_auto_p1_s4))
            ), sinceVersion = "5.2.1"),
        HelpTip(id = "cloud_delete_scope", title = Res.string.help_tip_cloud_delete_scope_title, subtitle = Res.string.help_tip_cloud_delete_scope_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Delete), category = HelpCategory.CLOUD_SYNC,
            deepLink = HelpRoute.Target.SETTINGS_GENERAL,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_cloud_delete_scope_p1_title, description = Res.string.help_tip_cloud_delete_scope_p1_desc, steps = listOf(Res.string.help_tip_cloud_delete_scope_p1_s1, Res.string.help_tip_cloud_delete_scope_p1_s2, Res.string.help_tip_cloud_delete_scope_p1_s3, Res.string.help_tip_cloud_delete_scope_p1_s4))
            ), sinceVersion = "5.2.2"),
        HelpTip(id = "cloud_albums_manage", title = Res.string.help_tip_cloud_albums_manage_title, subtitle = Res.string.help_tip_cloud_albums_manage_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.PhotoAlbum), category = HelpCategory.CLOUD_SYNC,
            deepLink = HelpRoute.Target.ALBUMS,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_cloud_albums_manage_p1_title, description = Res.string.help_tip_cloud_albums_manage_p1_desc, steps = listOf(Res.string.help_tip_cloud_albums_manage_p1_s1, Res.string.help_tip_cloud_albums_manage_p1_s2, Res.string.help_tip_cloud_albums_manage_p1_s3, Res.string.help_tip_cloud_albums_manage_p1_s4), previewType = PreviewType.ALBUM_GRID)
            ), sinceVersion = "5.2.2")
    )
    // endregion

    // region Favorites & Trash
    private val FAV_TRASH_TIPS = listOf(
        HelpTip(id = "fav_add", title = Res.string.help_tip_fav_add_title, subtitle = Res.string.help_tip_fav_add_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.FavoriteBorder), category = HelpCategory.FAVORITES_TRASH,
            pages = listOf(TutorialPage(title = Res.string.help_tip_fav_add_p1_title, description = Res.string.help_tip_fav_add_p1_desc, previewType = PreviewType.FAVORITES_GRID)), sinceVersion = "4.0.0"),
        HelpTip(id = "fav_browse", title = Res.string.help_tip_fav_browse_title, subtitle = Res.string.help_tip_fav_browse_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Favorite), category = HelpCategory.FAVORITES_TRASH,
            pages = listOf(TutorialPage(title = Res.string.help_tip_fav_browse_p1_title, description = Res.string.help_tip_fav_browse_p1_desc, previewType = PreviewType.FAVORITES_GRID)), sinceVersion = "4.0.0",
            quickActions = listOf(QuickAction(Res.string.help_qa_open_favorites, Icons.Outlined.Collections, HelpRoute.Target.FAVORITES))),
        HelpTip(id = "trash_delete", title = Res.string.help_tip_trash_delete_title, subtitle = Res.string.help_tip_trash_delete_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Delete), category = HelpCategory.FAVORITES_TRASH,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_trash_delete_p1_title, description = Res.string.help_tip_trash_delete_p1_desc, previewType = PreviewType.TRASH_GRID),
                TutorialPage(title = Res.string.help_tip_trash_delete_p2_title, description = Res.string.help_tip_trash_delete_p2_desc, steps = listOf(Res.string.help_tip_trash_delete_p2_s1, Res.string.help_tip_trash_delete_p2_s2, Res.string.help_tip_trash_delete_p2_s3, Res.string.help_tip_trash_delete_p2_s4), previewType = PreviewType.TRASH_GRID)
            ), sinceVersion = "4.0.0",
            quickActions = listOf(QuickAction(Res.string.help_qa_open_trash, Icons.Outlined.Collections, HelpRoute.Target.TRASHED))),
        HelpTip(id = "trash_enable", title = Res.string.help_tip_trash_enable_title, subtitle = Res.string.help_tip_trash_enable_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.RestoreFromTrash), category = HelpCategory.FAVORITES_TRASH,
            deepLink = HelpRoute.Target.SETTINGS_GENERAL,
            pages = listOf(TutorialPage(title = Res.string.help_tip_trash_enable_p1_title, description = Res.string.help_tip_trash_enable_p1_desc, steps = listOf(Res.string.help_tip_trash_enable_p1_s1, Res.string.help_tip_trash_enable_p1_s2, Res.string.help_tip_trash_enable_p1_s3, Res.string.help_tip_trash_enable_p1_s4))), sinceVersion = "4.0.0"),
        HelpTip(id = "trash_fav_redesign", title = Res.string.help_tip_trash_fav_redesign_title, subtitle = Res.string.help_tip_trash_fav_redesign_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.DesignServices), category = HelpCategory.FAVORITES_TRASH,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_trash_fav_redesign_p1_title, description = Res.string.help_tip_trash_fav_redesign_p1_desc, previewType = PreviewType.TRASH_GRID)
            ), sinceVersion = "4.2.3")
    )
    // endregion

    // region Locations
    private val LOCATION_TIPS = listOf(
        HelpTip(id = "location_browse", title = Res.string.help_tip_location_browse_title, subtitle = Res.string.help_tip_location_browse_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Map), category = HelpCategory.LOCATIONS,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_location_browse_p1_title, description = Res.string.help_tip_location_browse_p1_desc, previewType = PreviewType.LOCATION_MAP),
                TutorialPage(title = Res.string.help_tip_location_browse_p2_title, description = Res.string.help_tip_location_browse_p2_desc, previewType = PreviewType.LOCATION_MAP)
            ), sinceVersion = "4.0.0",
            quickActions = listOf(QuickAction(Res.string.help_qa_open_locations, Icons.Outlined.LocationOn, HelpRoute.Target.LOCATIONS))),
        HelpTip(id = "location_viewer", title = Res.string.help_tip_location_viewer_title, subtitle = Res.string.help_tip_location_viewer_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.LocationOn), category = HelpCategory.LOCATIONS,
            pages = listOf(TutorialPage(title = Res.string.help_tip_location_viewer_p1_title, description = Res.string.help_tip_location_viewer_p1_desc)), sinceVersion = "4.0.0")
    )
    // endregion

    // region Metadata
    private val METADATA_TIPS = listOf(
        HelpTip(id = "exif_view", title = Res.string.help_tip_exif_view_title, subtitle = Res.string.help_tip_exif_view_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Info), category = HelpCategory.METADATA,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_exif_view_p1_title, description = Res.string.help_tip_exif_view_p1_desc, previewType = PreviewType.EXIF_VIEWER),
                TutorialPage(title = Res.string.help_tip_exif_view_p2_title, description = Res.string.help_tip_exif_view_p2_desc, previewType = PreviewType.EXIF_VIEWER)
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "exif_edit", title = Res.string.help_tip_exif_edit_title, subtitle = Res.string.help_tip_exif_edit_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.EditNote), category = HelpCategory.METADATA,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_exif_edit_p1_title, description = Res.string.help_tip_exif_edit_p1_desc, previewType = PreviewType.EXIF_VIEWER),
                TutorialPage(title = Res.string.help_tip_exif_edit_p2_title, description = Res.string.help_tip_exif_edit_p2_desc, steps = listOf(Res.string.help_tip_exif_edit_p2_s1, Res.string.help_tip_exif_edit_p2_s2, Res.string.help_tip_exif_edit_p2_s3, Res.string.help_tip_exif_edit_p2_s4), previewType = PreviewType.EXIF_VIEWER)
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "exif_delete_location", title = Res.string.help_tip_exif_delete_location_title, subtitle = Res.string.help_tip_exif_delete_location_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.LocationOff), category = HelpCategory.METADATA,
            pages = listOf(TutorialPage(title = Res.string.help_tip_exif_delete_location_p1_title, description = Res.string.help_tip_exif_delete_location_p1_desc, steps = listOf(Res.string.help_tip_exif_delete_location_p1_s1, Res.string.help_tip_exif_delete_location_p1_s2, Res.string.help_tip_exif_delete_location_p1_s3, Res.string.help_tip_exif_delete_location_p1_s4))), sinceVersion = "4.0.0"),
        HelpTip(id = "exif_refresh", title = Res.string.help_tip_exif_refresh_title, subtitle = Res.string.help_tip_exif_refresh_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Refresh), category = HelpCategory.METADATA,
            deepLink = HelpRoute.Target.SETTINGS_SMART_FEATURES,
            pages = listOf(TutorialPage(title = Res.string.help_tip_exif_refresh_p1_title, description = Res.string.help_tip_exif_refresh_p1_desc, steps = listOf(Res.string.help_tip_exif_refresh_p1_s1, Res.string.help_tip_exif_refresh_p1_s2, Res.string.help_tip_exif_refresh_p1_s3, Res.string.help_tip_exif_refresh_p1_s4))), sinceVersion = "4.0.0"),
        HelpTip(id = "metadata_sanitize", title = Res.string.help_tip_metadata_sanitize_title, subtitle = Res.string.help_tip_metadata_sanitize_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.CleaningServices), category = HelpCategory.METADATA,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_metadata_sanitize_p1_title, description = Res.string.help_tip_metadata_sanitize_p1_desc, steps = listOf(Res.string.help_tip_metadata_sanitize_p1_s1, Res.string.help_tip_metadata_sanitize_p1_s2, Res.string.help_tip_metadata_sanitize_p1_s3, Res.string.help_tip_metadata_sanitize_p1_s4), previewType = PreviewType.EXIF_VIEWER)
            ), sinceVersion = "5.1.0")
    )
    // endregion

    // region Settings
    private val SETTINGS_APPEARANCE_TIPS = listOf(
        HelpTip(id = "settings_color_palette", title = Res.string.help_tip_settings_color_palette_title, subtitle = Res.string.help_tip_settings_color_palette_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Palette), category = HelpCategory.SETTINGS_APPEARANCE,
            deepLink = HelpRoute.Target.COLOR_PALETTE,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_settings_color_palette_p1_title, description = Res.string.help_tip_settings_color_palette_p1_desc, previewType = PreviewType.COLOR_PALETTE),
                TutorialPage(title = Res.string.help_tip_settings_color_palette_p2_title, description = Res.string.help_tip_settings_color_palette_p2_desc, steps = listOf(Res.string.help_tip_settings_color_palette_p2_s1, Res.string.help_tip_settings_color_palette_p2_s2, Res.string.help_tip_settings_color_palette_p2_s3), previewType = PreviewType.COLOR_PALETTE)
            ), sinceVersion = "4.1.0"),
        HelpTip(id = "settings_dark_mode", title = Res.string.help_tip_settings_dark_mode_title, subtitle = Res.string.help_tip_settings_dark_mode_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.DarkMode), category = HelpCategory.SETTINGS_APPEARANCE,
            deepLink = HelpRoute.Target.SETTINGS_APPEARANCE,
            pages = listOf(TutorialPage(title = Res.string.help_tip_settings_dark_mode_p1_title, description = Res.string.help_tip_settings_dark_mode_p1_desc, steps = listOf(Res.string.help_tip_settings_dark_mode_p1_s1, Res.string.help_tip_settings_dark_mode_p1_s2, Res.string.help_tip_settings_dark_mode_p1_s3), previewType = PreviewType.THEME_PICKER)), sinceVersion = "4.0.0"),
        HelpTip(id = "settings_amoled", title = Res.string.help_tip_settings_amoled_title, subtitle = Res.string.help_tip_settings_amoled_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.InvertColors), category = HelpCategory.SETTINGS_APPEARANCE,
            deepLink = HelpRoute.Target.SETTINGS_APPEARANCE,
            pages = listOf(TutorialPage(title = Res.string.help_tip_settings_amoled_p1_title, description = Res.string.help_tip_settings_amoled_p1_desc, steps = listOf(Res.string.help_tip_settings_amoled_p1_s1, Res.string.help_tip_settings_amoled_p1_s2, Res.string.help_tip_settings_amoled_p1_s3, Res.string.help_tip_settings_amoled_p1_s4), previewType = PreviewType.THEME_PICKER)), sinceVersion = "4.0.0")
    )

    private val SETTINGS_GENERAL_TIPS = listOf(
        HelpTip(id = "settings_trash_toggle", title = Res.string.help_tip_settings_trash_toggle_title, subtitle = Res.string.help_tip_settings_trash_toggle_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Delete), category = HelpCategory.SETTINGS_GENERAL,
            deepLink = HelpRoute.Target.SETTINGS_GENERAL,
            pages = listOf(TutorialPage(title = Res.string.help_tip_settings_trash_toggle_p1_title, description = Res.string.help_tip_settings_trash_toggle_p1_desc, steps = listOf(Res.string.help_tip_settings_trash_toggle_p1_s1, Res.string.help_tip_settings_trash_toggle_p1_s2, Res.string.help_tip_settings_trash_toggle_p1_s3), previewType = PreviewType.SETTINGS_GENERAL)), sinceVersion = "4.0.0"),
        HelpTip(id = "settings_trash_confirm", title = Res.string.help_tip_settings_trash_confirm_title, subtitle = Res.string.help_tip_settings_trash_confirm_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.DeleteForever), category = HelpCategory.SETTINGS_GENERAL,
            deepLink = HelpRoute.Target.SETTINGS_GENERAL,
            pages = listOf(TutorialPage(title = Res.string.help_tip_settings_trash_confirm_p1_title, description = Res.string.help_tip_settings_trash_confirm_p1_desc, steps = listOf(Res.string.help_tip_settings_trash_confirm_p1_s1, Res.string.help_tip_settings_trash_confirm_p1_s2, Res.string.help_tip_settings_trash_confirm_p1_s3))), sinceVersion = "4.0.0"),
        HelpTip(id = "settings_secure_mode", title = Res.string.help_tip_settings_secure_mode_title, subtitle = Res.string.help_tip_settings_secure_mode_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Lock), category = HelpCategory.SETTINGS_GENERAL,
            deepLink = HelpRoute.Target.SETTINGS_GENERAL,
            pages = listOf(TutorialPage(title = Res.string.help_tip_settings_secure_mode_p1_title, description = Res.string.help_tip_settings_secure_mode_p1_desc, steps = listOf(Res.string.help_tip_settings_secure_mode_p1_s1, Res.string.help_tip_settings_secure_mode_p1_s2, Res.string.help_tip_settings_secure_mode_p1_s3, Res.string.help_tip_settings_secure_mode_p1_s4), previewType = PreviewType.SETTINGS_GENERAL)), sinceVersion = "4.0.0"),
        HelpTip(id = "settings_vibrations", title = Res.string.help_tip_settings_vibrations_title, subtitle = Res.string.help_tip_settings_vibrations_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Vibration), category = HelpCategory.SETTINGS_GENERAL,
            deepLink = HelpRoute.Target.SETTINGS_GENERAL,
            pages = listOf(TutorialPage(title = Res.string.help_tip_settings_vibrations_p1_title, description = Res.string.help_tip_settings_vibrations_p1_desc, steps = listOf(Res.string.help_tip_settings_vibrations_p1_s1, Res.string.help_tip_settings_vibrations_p1_s2, Res.string.help_tip_settings_vibrations_p1_s3))), sinceVersion = "4.0.0"),
        HelpTip(id = "settings_backup_restore", title = Res.string.help_tip_settings_backup_restore_title, subtitle = Res.string.help_tip_settings_backup_restore_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.SettingsBackupRestore), category = HelpCategory.SETTINGS_GENERAL,
            deepLink = HelpRoute.Target.SETTINGS_BACKUP,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_settings_backup_restore_p1_title, description = Res.string.help_tip_settings_backup_restore_p1_desc),
                TutorialPage(title = Res.string.help_tip_settings_backup_restore_p2_title, description = Res.string.help_tip_settings_backup_restore_p2_desc, steps = listOf(Res.string.help_tip_settings_backup_restore_p2_s1, Res.string.help_tip_settings_backup_restore_p2_s2, Res.string.help_tip_settings_backup_restore_p2_s3, Res.string.help_tip_settings_backup_restore_p2_s4)),
                TutorialPage(title = Res.string.help_tip_settings_backup_restore_p3_title, description = Res.string.help_tip_settings_backup_restore_p3_desc, steps = listOf(Res.string.help_tip_settings_backup_restore_p3_s1, Res.string.help_tip_settings_backup_restore_p3_s2, Res.string.help_tip_settings_backup_restore_p3_s3))
            ), sinceVersion = "5.0.0"),
        HelpTip(id = "settings_slideshow", title = Res.string.help_tip_settings_slideshow_title, subtitle = Res.string.help_tip_settings_slideshow_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Slideshow), category = HelpCategory.SETTINGS_GENERAL,
            deepLink = HelpRoute.Target.SLIDESHOW_SETTINGS,
            pages = listOf(TutorialPage(title = Res.string.help_tip_settings_slideshow_p1_title, description = Res.string.help_tip_settings_slideshow_p1_desc, steps = listOf(Res.string.help_tip_settings_slideshow_p1_s1, Res.string.help_tip_settings_slideshow_p1_s2, Res.string.help_tip_settings_slideshow_p1_s3))), sinceVersion = "5.1.0"),
        HelpTip(id = "settings_date_format", title = Res.string.help_tip_settings_date_format_title, subtitle = Res.string.help_tip_settings_date_format_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.CalendarMonth), category = HelpCategory.SETTINGS_GENERAL,
            deepLink = HelpRoute.Target.DATE_FORMAT,
            pages = listOf(TutorialPage(title = Res.string.help_tip_settings_date_format_p1_title, description = Res.string.help_tip_settings_date_format_p1_desc, steps = listOf(Res.string.help_tip_settings_date_format_p1_s1, Res.string.help_tip_settings_date_format_p1_s2, Res.string.help_tip_settings_date_format_p1_s3))), sinceVersion = "5.1.0")
    )

    private val SETTINGS_NAV_TIPS = listOf(
        HelpTip(id = "settings_launch_screen", title = Res.string.help_tip_settings_launch_screen_title, subtitle = Res.string.help_tip_settings_launch_screen_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Start), category = HelpCategory.SETTINGS_NAVIGATION,
            deepLink = HelpRoute.Target.SETTINGS_NAVIGATION,
            pages = listOf(TutorialPage(title = Res.string.help_tip_settings_launch_screen_p1_title, description = Res.string.help_tip_settings_launch_screen_p1_desc, steps = listOf(Res.string.help_tip_settings_launch_screen_p1_s1, Res.string.help_tip_settings_launch_screen_p1_s2, Res.string.help_tip_settings_launch_screen_p1_s3))), sinceVersion = "4.0.0"),
        HelpTip(id = "settings_old_navbar", title = Res.string.help_tip_settings_old_navbar_title, subtitle = Res.string.help_tip_settings_old_navbar_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.History), category = HelpCategory.SETTINGS_NAVIGATION,
            deepLink = HelpRoute.Target.SETTINGS_NAVIGATION,
            pages = listOf(TutorialPage(title = Res.string.help_tip_settings_old_navbar_p1_title, description = Res.string.help_tip_settings_old_navbar_p1_desc, steps = listOf(Res.string.help_tip_settings_old_navbar_p1_s1, Res.string.help_tip_settings_old_navbar_p1_s2, Res.string.help_tip_settings_old_navbar_p1_s3), previewType = PreviewType.NAV_BAR_PREVIEW)), sinceVersion = "4.0.0"),
        HelpTip(id = "settings_selection_titles", title = Res.string.help_tip_settings_selection_titles_title, subtitle = Res.string.help_tip_settings_selection_titles_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Title), category = HelpCategory.SETTINGS_NAVIGATION,
            deepLink = HelpRoute.Target.SETTINGS_NAVIGATION,
            pages = listOf(TutorialPage(title = Res.string.help_tip_settings_selection_titles_p1_title, description = Res.string.help_tip_settings_selection_titles_p1_desc, steps = listOf(Res.string.help_tip_settings_selection_titles_p1_s1, Res.string.help_tip_settings_selection_titles_p1_s2, Res.string.help_tip_settings_selection_titles_p1_s3, Res.string.help_tip_settings_selection_titles_p1_s4))), sinceVersion = "4.1.0"),
        HelpTip(id = "settings_selection_actions", title = Res.string.help_tip_settings_selection_actions_title, subtitle = Res.string.help_tip_settings_selection_actions_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Checklist), category = HelpCategory.SETTINGS_NAVIGATION,
            deepLink = HelpRoute.Target.SETTINGS_SELECTION_ACTIONS,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_settings_selection_actions_p1_title, description = Res.string.help_tip_settings_selection_actions_p1_desc),
                TutorialPage(title = Res.string.help_tip_settings_selection_actions_p2_title, description = Res.string.help_tip_settings_selection_actions_p2_desc, steps = listOf(Res.string.help_tip_settings_selection_actions_p2_s1, Res.string.help_tip_settings_selection_actions_p2_s2, Res.string.help_tip_settings_selection_actions_p2_s3, Res.string.help_tip_settings_selection_actions_p2_s4))
            ), sinceVersion = "4.0.0")
    )

    private val SETTINGS_SMART_TIPS = listOf(
        HelpTip(id = "settings_ai_models", title = Res.string.help_tip_settings_ai_models_title, subtitle = Res.string.help_tip_settings_ai_models_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.AutoAwesome), category = HelpCategory.SETTINGS_SMART,
            deepLink = HelpRoute.Target.AI_MODELS_MANAGER,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_settings_ai_models_p1_title, description = Res.string.help_tip_settings_ai_models_p1_desc),
                TutorialPage(title = Res.string.help_tip_settings_ai_models_p2_title, description = Res.string.help_tip_settings_ai_models_p2_desc, steps = listOf(Res.string.help_tip_settings_ai_models_p2_s1, Res.string.help_tip_settings_ai_models_p2_s2, Res.string.help_tip_settings_ai_models_p2_s3), actionLabel = Res.string.help_action_open_settings)
            ), sinceVersion = "4.1.0"),
        HelpTip(id = "settings_edit_backups", title = Res.string.help_tip_settings_edit_backups_title, subtitle = Res.string.help_tip_settings_edit_backups_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.SettingsBackupRestore), category = HelpCategory.SETTINGS_SMART,
            deepLink = HelpRoute.Target.EDIT_BACKUPS_VIEWER,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_settings_edit_backups_p1_title, description = Res.string.help_tip_settings_edit_backups_p1_desc),
                TutorialPage(title = Res.string.help_tip_settings_edit_backups_p2_title, description = Res.string.help_tip_settings_edit_backups_p2_desc, steps = listOf(Res.string.help_tip_settings_edit_backups_p2_s1, Res.string.help_tip_settings_edit_backups_p2_s2, Res.string.help_tip_settings_edit_backups_p2_s3))
            ), sinceVersion = "4.0.0")
    )

    private val SETTINGS_SECURITY_TIPS = listOf(
        HelpTip(id = "security_sandbox", title = Res.string.help_tip_security_sandbox_title, subtitle = Res.string.help_tip_security_sandbox_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Security), category = HelpCategory.SETTINGS_SECURITY,
            deepLink = HelpRoute.Target.SETTINGS_SECURITY,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_security_sandbox_p1_title, description = Res.string.help_tip_security_sandbox_p1_desc),
                TutorialPage(title = Res.string.help_tip_security_sandbox_p2_title, description = Res.string.help_tip_security_sandbox_p2_desc, steps = listOf(Res.string.help_tip_security_sandbox_p2_s1, Res.string.help_tip_security_sandbox_p2_s2, Res.string.help_tip_security_sandbox_p2_s3))
            ), sinceVersion = "4.2.2"),
        HelpTip(id = "security_encrypted_storage", title = Res.string.help_tip_security_encrypted_storage_title, subtitle = Res.string.help_tip_security_encrypted_storage_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.EnhancedEncryption), category = HelpCategory.SETTINGS_SECURITY,
            deepLink = HelpRoute.Target.SETTINGS_SECURITY,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_security_encrypted_storage_p1_title, description = Res.string.help_tip_security_encrypted_storage_p1_desc),
                TutorialPage(title = Res.string.help_tip_security_encrypted_storage_p2_title, description = Res.string.help_tip_security_encrypted_storage_p2_desc, steps = listOf(Res.string.help_tip_security_encrypted_storage_p2_s1, Res.string.help_tip_security_encrypted_storage_p2_s2, Res.string.help_tip_security_encrypted_storage_p2_s3))
            ), sinceVersion = "4.2.2"),
        HelpTip(id = "security_private_folder", title = Res.string.help_tip_security_private_folder_title, subtitle = Res.string.help_tip_security_private_folder_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Shield), category = HelpCategory.SETTINGS_SECURITY,
            deepLink = HelpRoute.Target.SETTINGS_SECURITY,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_security_private_folder_p1_title, description = Res.string.help_tip_security_private_folder_p1_desc),
                TutorialPage(title = Res.string.help_tip_security_private_folder_p2_title, description = Res.string.help_tip_security_private_folder_p2_desc, steps = listOf(Res.string.help_tip_security_private_folder_p2_s1, Res.string.help_tip_security_private_folder_p2_s2, Res.string.help_tip_security_private_folder_p2_s3))
            ), sinceVersion = "4.2.2"),
        HelpTip(id = "security_advanced_protection", title = Res.string.help_tip_security_aapm_title, subtitle = Res.string.help_tip_security_aapm_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.GppGood), category = HelpCategory.SETTINGS_SECURITY,
            deepLink = HelpRoute.Target.SETTINGS_SECURITY,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_security_aapm_p1_title, description = Res.string.help_tip_security_aapm_p1_desc),
                TutorialPage(title = Res.string.help_tip_security_aapm_p2_title, description = Res.string.help_tip_security_aapm_p2_desc, steps = listOf(Res.string.help_tip_security_aapm_p2_s1, Res.string.help_tip_security_aapm_p2_s2, Res.string.help_tip_security_aapm_p2_s3))
            ), sinceVersion = "5.0.0")
    )
    // endregion

    // region Gestures & Selection
    private val GESTURE_TIPS = listOf(
        HelpTip(id = "gesture_pinch_grid", title = Res.string.help_tip_gesture_pinch_grid_title, subtitle = Res.string.help_tip_gesture_pinch_grid_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Pinch), category = HelpCategory.GESTURES,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_gesture_pinch_grid_p1_title, description = Res.string.help_tip_gesture_pinch_grid_p1_desc, previewType = PreviewType.PINCH_ZOOM_GRID),
                TutorialPage(title = Res.string.help_tip_gesture_pinch_grid_p2_title, description = Res.string.help_tip_gesture_pinch_grid_p2_desc, previewType = PreviewType.PINCH_ZOOM_GRID)
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "gesture_swipe_viewer", title = Res.string.help_tip_gesture_swipe_viewer_title, subtitle = Res.string.help_tip_gesture_swipe_viewer_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Swipe), category = HelpCategory.GESTURES,
            pages = listOf(TutorialPage(title = Res.string.help_tip_gesture_swipe_viewer_p1_title, description = Res.string.help_tip_gesture_swipe_viewer_p1_desc, previewType = PreviewType.MEDIA_VIEWER)), sinceVersion = "4.0.0"),
        HelpTip(id = "gesture_long_press", title = Res.string.help_tip_gesture_long_press_title, subtitle = Res.string.help_tip_gesture_long_press_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.TouchApp), category = HelpCategory.GESTURES,
            pages = listOf(TutorialPage(title = Res.string.help_tip_gesture_long_press_p1_title, description = Res.string.help_tip_gesture_long_press_p1_desc, previewType = PreviewType.TIMELINE_GRID)), sinceVersion = "4.0.0"),
        HelpTip(id = "gesture_multi_select", title = Res.string.help_tip_gesture_multi_select_title, subtitle = Res.string.help_tip_gesture_multi_select_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.SelectAll), category = HelpCategory.GESTURES,
            pages = listOf(TutorialPage(title = Res.string.help_tip_gesture_multi_select_p1_title, description = Res.string.help_tip_gesture_multi_select_p1_desc, previewType = PreviewType.TIMELINE_GRID)), sinceVersion = "4.0.0")
    )

    private val SELECTION_TIPS = listOf(
        HelpTip(id = "selection_sheet", title = Res.string.help_tip_selection_sheet_title, subtitle = Res.string.help_tip_selection_sheet_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.LibraryAddCheck), category = HelpCategory.SELECTION_ACTIONS,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_selection_sheet_p1_title, description = Res.string.help_tip_selection_sheet_p1_desc),
                TutorialPage(title = Res.string.help_tip_selection_sheet_p2_title, description = Res.string.help_tip_selection_sheet_p2_desc, steps = listOf(Res.string.help_tip_selection_sheet_p2_s1, Res.string.help_tip_selection_sheet_p2_s2, Res.string.help_tip_selection_sheet_p2_s3, Res.string.help_tip_selection_sheet_p2_s4))
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "selection_batch_share", title = Res.string.help_tip_selection_batch_share_title, subtitle = Res.string.help_tip_selection_batch_share_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Share), category = HelpCategory.SELECTION_ACTIONS,
            pages = listOf(TutorialPage(title = Res.string.help_tip_selection_batch_share_p1_title, description = Res.string.help_tip_selection_batch_share_p1_desc, steps = listOf(Res.string.help_tip_selection_batch_share_p1_s1, Res.string.help_tip_selection_batch_share_p1_s2, Res.string.help_tip_selection_batch_share_p1_s3, Res.string.help_tip_selection_batch_share_p1_s4))), sinceVersion = "4.0.0"),
        HelpTip(id = "selection_batch_delete", title = Res.string.help_tip_selection_batch_delete_title, subtitle = Res.string.help_tip_selection_batch_delete_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Delete), category = HelpCategory.SELECTION_ACTIONS,
            pages = listOf(TutorialPage(title = Res.string.help_tip_selection_batch_delete_p1_title, description = Res.string.help_tip_selection_batch_delete_p1_desc, steps = listOf(Res.string.help_tip_selection_batch_delete_p1_s1, Res.string.help_tip_selection_batch_delete_p1_s2, Res.string.help_tip_selection_batch_delete_p1_s3, Res.string.help_tip_selection_batch_delete_p1_s4))), sinceVersion = "4.0.0"),
        HelpTip(id = "selection_right_align", title = Res.string.help_tip_selection_right_align_title, subtitle = Res.string.help_tip_selection_right_align_subtitle,
            icon = HelpIcon.ofVector(Icons.AutoMirrored.Outlined.FormatAlignRight), category = HelpCategory.SELECTION_ACTIONS,
            deepLink = HelpRoute.Target.SETTINGS_SELECTION_ACTIONS,
            pages = listOf(TutorialPage(title = Res.string.help_tip_selection_right_align_p1_title, description = Res.string.help_tip_selection_right_align_p1_desc, steps = listOf(Res.string.help_tip_selection_right_align_p1_s1, Res.string.help_tip_selection_right_align_p1_s2, Res.string.help_tip_selection_right_align_p1_s3))), sinceVersion = "4.2.1")
    )
    // endregion

    // region Accessibility
    private val ACCESSIBILITY_TIPS = listOf(
        HelpTip(id = "accessibility_font", title = Res.string.help_tip_accessibility_font_title, subtitle = Res.string.help_tip_accessibility_font_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.FormatSize), category = HelpCategory.ACCESSIBILITY,
            pages = listOf(TutorialPage(title = Res.string.help_tip_accessibility_font_p1_title, description = Res.string.help_tip_accessibility_font_p1_desc)), sinceVersion = "4.0.0"),
        HelpTip(id = "advanced_media_picker", title = Res.string.help_tip_advanced_media_picker_title, subtitle = Res.string.help_tip_advanced_media_picker_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.PhotoLibrary), category = HelpCategory.ACCESSIBILITY,
            pages = listOf(TutorialPage(title = Res.string.help_tip_advanced_media_picker_p1_title, description = Res.string.help_tip_advanced_media_picker_p1_desc)), sinceVersion = "4.0.0"),
        HelpTip(id = "advanced_standalone", title = Res.string.help_tip_advanced_standalone_title, subtitle = Res.string.help_tip_advanced_standalone_subtitle,
            icon = HelpIcon.ofVector(Icons.AutoMirrored.Outlined.Launch), category = HelpCategory.ACCESSIBILITY,
            pages = listOf(TutorialPage(title = Res.string.help_tip_advanced_standalone_p1_title, description = Res.string.help_tip_advanced_standalone_p1_desc)), sinceVersion = "4.0.0"),
        HelpTip(id = "advanced_wallpaper", title = Res.string.help_tip_advanced_wallpaper_title, subtitle = Res.string.help_tip_advanced_wallpaper_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Wallpaper), category = HelpCategory.ACCESSIBILITY,
            pages = listOf(TutorialPage(title = Res.string.help_tip_advanced_wallpaper_p1_title, description = Res.string.help_tip_advanced_wallpaper_p1_desc, steps = listOf(Res.string.help_tip_advanced_wallpaper_p1_s1, Res.string.help_tip_advanced_wallpaper_p1_s2, Res.string.help_tip_advanced_wallpaper_p1_s3, Res.string.help_tip_advanced_wallpaper_p1_s4, Res.string.help_tip_advanced_wallpaper_p1_s5))), sinceVersion = "4.0.0"),
        HelpTip(id = "accessibility_remote_settings", title = Res.string.help_tip_accessibility_remote_settings_title, subtitle = Res.string.help_tip_accessibility_remote_settings_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Settings), category = HelpCategory.ACCESSIBILITY,
            deepLink = HelpRoute.Target.SETTINGS,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_accessibility_remote_settings_p1_title, description = Res.string.help_tip_accessibility_remote_settings_p1_desc, steps = listOf(Res.string.help_tip_accessibility_remote_settings_p1_s1, Res.string.help_tip_accessibility_remote_settings_p1_s2, Res.string.help_tip_accessibility_remote_settings_p1_s3, Res.string.help_tip_accessibility_remote_settings_p1_s4), previewType = PreviewType.SETTINGS_GENERAL)
            ), sinceVersion = "5.1.1"),
        HelpTip(id = "advanced_developer", title = Res.string.help_tip_advanced_developer_title, subtitle = Res.string.help_tip_advanced_developer_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.BugReport), category = HelpCategory.ACCESSIBILITY,
            deepLink = HelpRoute.Target.DEVELOPER,
            pages = listOf(
                TutorialPage(title = Res.string.help_tip_advanced_developer_p1_title, description = Res.string.help_tip_advanced_developer_p1_desc, steps = listOf(Res.string.help_tip_advanced_developer_p1_s1, Res.string.help_tip_advanced_developer_p1_s2, Res.string.help_tip_advanced_developer_p1_s3, Res.string.help_tip_advanced_developer_p1_s4), previewType = PreviewType.SETTINGS_GENERAL)
            ), sinceVersion = "5.2.2")
    )
    // endregion

    // region Aggregation
    private val ALL_TIPS: List<HelpTip> = BASICS_TIPS + NAVIGATION_TIPS + PERSONALIZATION_TIPS +
        TIMELINE_ALBUM_TIPS + VIEWING_TIPS + VIEWER_ACTION_TIPS + VIEWER_SETTINGS_TIPS +
        EDITING_TIPS + SEARCH_TIPS + AI_TIPS + ALBUM_TIPS + VAULT_TIPS + CLOUD_TIPS +
        FAV_TRASH_TIPS + LOCATION_TIPS + METADATA_TIPS +
        SETTINGS_APPEARANCE_TIPS + SETTINGS_GENERAL_TIPS + SETTINGS_NAV_TIPS + SETTINGS_SMART_TIPS +
        SETTINGS_SECURITY_TIPS + GESTURE_TIPS + SELECTION_TIPS + ACCESSIBILITY_TIPS

    // endregion
}
