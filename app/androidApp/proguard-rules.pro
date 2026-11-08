# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn org.slf4j.impl.StaticLoggerBinder

-keep class com.dot.gallery.feature.editor.adjustments.** { *; }
-keep class com.drew.** { *; }
-keep class java.io.** { *; }
-keep class com.adobe.** { *; }
-keep class ai.onnxruntime.** { *; }

-dontwarn com.google.auto.value.AutoValue$Builder
-dontwarn com.google.auto.value.AutoValue
-dontwarn org.tensorflow.lite.gpu.GpuDelegateFactory$Options$GpuBackend
-dontwarn org.tensorflow.lite.gpu.GpuDelegateFactory$Options

# Keep custom Glide decoders and model loaders (HEIF/JXL/Encrypted)
-keep class com.dot.gallery.core.decoder.glide.** { *; }
-keep class com.radzivon.bartoshyk.avif.** { *; }
-keep class com.awxkee.jxlcoder.** { *; }
-dontwarn com.radzivon.bartoshyk.avif.**
-dontwarn com.awxkee.jxlcoder.**

# Room & Room3 Database keep rules
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep class * extends androidx.room.RoomDatabase_Impl {
    <init>();
}
-keep class * extends androidx.room3.RoomDatabase {
    <init>();
}
-keep class * extends androidx.room3.RoomDatabase_Impl {
    <init>();
}
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room3.Entity class * { *; }
-keep @androidx.room3.Dao interface * { *; }
-dontwarn androidx.room.**
-dontwarn androidx.room3.**

# AndroidX WorkManager & WorkDatabase
-keep class androidx.work.impl.WorkDatabase** { *; }
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.InputMerger {
    public <init>();
}
-keep class * extends androidx.work.WorkerFactory {
    public <init>();
}
-keep class androidx.work.OverwritingInputMerger {
    public <init>();
}
-keep class androidx.work.ArrayCreatingInputMerger {
    public <init>();
}
-keep class dev.brewkits.kmpworkmanager.** { *; }
-keep class dev.brewkits.kmpworker.** { *; }

# Cloud provider models (Retrofit DTOs, Room entities, serialization)
-keep class com.dot.gallery.cloud.** { *; }
-keep class com.dot.gallery.cloud.data.entity.** { *; }
-keep class com.dot.gallery.cloud.core.** { *; }
-keep class com.dot.gallery.cloud.image.** { *; }

-dontwarn javax.el.BeanELResolver
-dontwarn javax.el.ELContext
-dontwarn javax.el.ELResolver
-dontwarn javax.el.ExpressionFactory
-dontwarn javax.el.FunctionMapper
-dontwarn javax.el.ValueExpression
-dontwarn javax.el.VariableMapper
-dontwarn org.ietf.jgss.GSSContext
-dontwarn org.ietf.jgss.GSSCredential
-dontwarn org.ietf.jgss.GSSException
-dontwarn org.ietf.jgss.GSSManager
-dontwarn org.ietf.jgss.GSSName
-dontwarn org.ietf.jgss.Oid

-dontwarn com.badlogic.gdx.graphics.Pixmap$Format
-dontwarn com.badlogic.gdx.graphics.Pixmap
-dontwarn com.jogamp.common.os.Platform
-dontwarn com.jogamp.opencl.CLBuffer
-dontwarn com.jogamp.opencl.CLCommandQueue
-dontwarn com.jogamp.opencl.CLContext
-dontwarn com.jogamp.opencl.CLDevice
-dontwarn com.jogamp.opencl.CLEventList
-dontwarn com.jogamp.opencl.CLImage2d
-dontwarn com.jogamp.opencl.CLImageFormat$ChannelOrder
-dontwarn com.jogamp.opencl.CLImageFormat$ChannelType
-dontwarn com.jogamp.opencl.CLImageFormat
-dontwarn com.jogamp.opencl.CLKernel
-dontwarn com.jogamp.opencl.CLMemory$Map
-dontwarn com.jogamp.opencl.CLMemory$Mem
-dontwarn com.jogamp.opencl.CLMemory
-dontwarn com.jogamp.opencl.CLObject
-dontwarn com.jogamp.opencl.CLPlatform
-dontwarn com.jogamp.opencl.CLProgram
-dontwarn com.jogamp.opencl.gl.CLGLContext
-dontwarn com.jogamp.opencl.gl.CLGLImage2d
-dontwarn com.jogamp.opencl.gl.CLGLObject
-dontwarn com.jogamp.opengl.GL2
-dontwarn com.jogamp.opengl.GL
-dontwarn com.jogamp.opengl.GLAutoDrawable
-dontwarn com.jogamp.opengl.GLCapabilities
-dontwarn com.jogamp.opengl.GLCapabilitiesImmutable
-dontwarn com.jogamp.opengl.GLContext
-dontwarn com.jogamp.opengl.GLDrawable
-dontwarn com.jogamp.opengl.GLEventListener
-dontwarn com.jogamp.opengl.GLProfile
-dontwarn com.jogamp.opengl.awt.GLCanvas
-dontwarn com.jogamp.opengl.glu.GLU
-dontwarn com.jogamp.opengl.util.Gamma
-dontwarn java.awt.Canvas
-dontwarn java.awt.Color
-dontwarn java.awt.Component
-dontwarn java.awt.Container
-dontwarn java.awt.Dimension
-dontwarn java.awt.DisplayMode
-dontwarn java.awt.EventQueue
-dontwarn java.awt.Graphics2D
-dontwarn java.awt.Graphics
-dontwarn java.awt.GraphicsConfiguration
-dontwarn java.awt.GraphicsDevice
-dontwarn java.awt.GraphicsEnvironment
-dontwarn java.awt.Image
-dontwarn java.awt.KeyEventDispatcher
-dontwarn java.awt.KeyboardFocusManager
-dontwarn java.awt.Point
-dontwarn java.awt.Rectangle
-dontwarn java.awt.Window
-dontwarn java.awt.color.ColorSpace
-dontwarn java.awt.color.ICC_ColorSpace
-dontwarn java.awt.color.ICC_Profile
-dontwarn java.awt.color.ICC_ProfileRGB
-dontwarn java.awt.event.ComponentAdapter
-dontwarn java.awt.event.ComponentEvent
-dontwarn java.awt.event.ComponentListener
-dontwarn java.awt.event.KeyEvent
-dontwarn java.awt.image.BufferStrategy
-dontwarn java.awt.image.BufferedImage
-dontwarn java.awt.image.ColorModel
-dontwarn java.awt.image.ComponentColorModel
-dontwarn java.awt.image.ComponentSampleModel
-dontwarn java.awt.image.DataBuffer
-dontwarn java.awt.image.DataBufferByte
-dontwarn java.awt.image.DataBufferDouble
-dontwarn java.awt.image.DataBufferFloat
-dontwarn java.awt.image.DataBufferInt
-dontwarn java.awt.image.DataBufferShort
-dontwarn java.awt.image.DataBufferUShort
-dontwarn java.awt.image.ImageObserver
-dontwarn java.awt.image.MultiPixelPackedSampleModel
-dontwarn java.awt.image.Raster
-dontwarn java.awt.image.SampleModel
-dontwarn java.awt.image.SinglePixelPackedSampleModel
-dontwarn java.awt.image.WritableRaster
-dontwarn java.beans.PropertyEditorSupport
-dontwarn java.beans.PropertyVetoException
-dontwarn java.beans.beancontext.BeanContextChildSupport
-dontwarn java.beans.beancontext.BeanContextSupport
-dontwarn java.lang.management.BufferPoolMXBean
-dontwarn java.lang.management.ManagementFactory
-dontwarn javafx.scene.image.Image
-dontwarn javafx.scene.image.PixelFormat
-dontwarn javafx.scene.image.PixelReader
-dontwarn javafx.scene.image.WritableImage
-dontwarn javafx.scene.image.WritablePixelFormat
-dontwarn javafx.scene.paint.Color
-dontwarn javax.imageio.ImageIO
-dontwarn javax.management.InstanceAlreadyExistsException
-dontwarn javax.management.MBeanRegistrationException
-dontwarn javax.management.MBeanServer
-dontwarn javax.management.MalformedObjectNameException
-dontwarn javax.management.NotCompliantMBeanException
-dontwarn javax.management.ObjectInstance
-dontwarn javax.management.ObjectName
-dontwarn javax.swing.JFrame
-dontwarn javax.swing.JOptionPane
-dontwarn javax.swing.JRootPane
-dontwarn kotlinx.parcelize.Parcelize
-dontwarn org.apache.maven.artifact.Artifact
-dontwarn org.apache.maven.model.Build
-dontwarn org.apache.maven.plugin.AbstractMojo
-dontwarn org.apache.maven.plugin.MojoExecutionException
-dontwarn org.apache.maven.plugin.descriptor.PluginDescriptor
-dontwarn org.apache.maven.plugin.logging.Log
-dontwarn org.apache.maven.plugins.annotations.Mojo
-dontwarn org.apache.maven.plugins.annotations.Parameter
-dontwarn org.apache.maven.project.MavenProject
-dontwarn org.bytedeco.artoolkitplus.ARMarkerInfo
-dontwarn org.bytedeco.artoolkitplus.MultiTracker
-dontwarn org.bytedeco.artoolkitplus.global.ARToolKitPlus
-dontwarn org.bytedeco.flycapture.FlyCapture2.BusManager
-dontwarn org.bytedeco.flycapture.FlyCapture2.Camera
-dontwarn org.bytedeco.flycapture.FlyCapture2.CameraInfo
-dontwarn org.bytedeco.flycapture.FlyCapture2.Error
-dontwarn org.bytedeco.flycapture.FlyCapture2.FC2Config
-dontwarn org.bytedeco.flycapture.FlyCapture2.Image
-dontwarn org.bytedeco.flycapture.FlyCapture2.PGRGuid
-dontwarn org.bytedeco.flycapture.FlyCapture2.Property
-dontwarn org.bytedeco.flycapture.FlyCapture2.TimeStamp
-dontwarn org.bytedeco.flycapture.FlyCapture2.TriggerMode
-dontwarn org.bytedeco.flycapture.global.FlyCapture2
-dontwarn org.bytedeco.leptonica.PIX
-dontwarn org.bytedeco.leptonica.PIXCMAP
-dontwarn org.bytedeco.leptonica.global.leptonica
-dontwarn org.bytedeco.libdc1394.dc1394_t
-dontwarn org.bytedeco.libdc1394.dc1394camera_id_t
-dontwarn org.bytedeco.libdc1394.dc1394camera_list_t
-dontwarn org.bytedeco.libdc1394.dc1394camera_t
-dontwarn org.bytedeco.libdc1394.dc1394video_frame_t
-dontwarn org.bytedeco.libdc1394.global.dc1394
-dontwarn org.bytedeco.libdc1394.presets.dc1394$pollfd
-dontwarn org.bytedeco.libfreenect.freenect_context
-dontwarn org.bytedeco.libfreenect.freenect_usb_context
-dontwarn org.bytedeco.libfreenect.global.freenect
-dontwarn org.bytedeco.libfreenect2.Frame
-dontwarn org.bytedeco.libfreenect2.FrameListener
-dontwarn org.bytedeco.libfreenect2.FrameMap
-dontwarn org.bytedeco.libfreenect2.Freenect2
-dontwarn org.bytedeco.libfreenect2.Freenect2Device
-dontwarn org.bytedeco.libfreenect2.PacketPipeline
-dontwarn org.bytedeco.libfreenect2.SyncMultiFrameListener
-dontwarn org.bytedeco.libfreenect2.global.freenect2
-dontwarn org.bytedeco.librealsense.context
-dontwarn org.bytedeco.librealsense.device
-dontwarn org.bytedeco.librealsense.global.RealSense
-dontwarn org.bytedeco.librealsense2.global.realsense2
-dontwarn org.bytedeco.librealsense2.presets.realsense2
-dontwarn org.bytedeco.librealsense2.rs2_config
-dontwarn org.bytedeco.librealsense2.rs2_context
-dontwarn org.bytedeco.librealsense2.rs2_device
-dontwarn org.bytedeco.librealsense2.rs2_device_list
-dontwarn org.bytedeco.librealsense2.rs2_error
-dontwarn org.bytedeco.librealsense2.rs2_frame
-dontwarn org.bytedeco.librealsense2.rs2_options
-dontwarn org.bytedeco.librealsense2.rs2_pipeline
-dontwarn org.bytedeco.librealsense2.rs2_pipeline_profile
-dontwarn org.bytedeco.librealsense2.rs2_sensor
-dontwarn org.bytedeco.librealsense2.rs2_sensor_list
-dontwarn org.bytedeco.librealsense2.rs2_stream_profile
-dontwarn org.bytedeco.librealsense2.rs2_stream_profile_list
-dontwarn org.bytedeco.opencv.cvkernels$KernelData
-dontwarn org.bytedeco.opencv.cvkernels
-dontwarn org.bytedeco.opencv.global.opencv_calib3d
-dontwarn org.bytedeco.opencv.global.opencv_core
-dontwarn org.bytedeco.opencv.global.opencv_highgui
-dontwarn org.bytedeco.opencv.global.opencv_imgcodecs
-dontwarn org.bytedeco.opencv.global.opencv_imgproc
-dontwarn org.bytedeco.opencv.opencv_core.CvArr
-dontwarn org.bytedeco.opencv.opencv_core.CvBox2D
-dontwarn org.bytedeco.opencv.opencv_core.CvContour
-dontwarn org.bytedeco.opencv.opencv_core.CvErrorCallback
-dontwarn org.bytedeco.opencv.opencv_core.CvMat
-dontwarn org.bytedeco.opencv.opencv_core.CvMemStorage
-dontwarn org.bytedeco.opencv.opencv_core.CvPoint2D32f
-dontwarn org.bytedeco.opencv.opencv_core.CvPoint
-dontwarn org.bytedeco.opencv.opencv_core.CvRect
-dontwarn org.bytedeco.opencv.opencv_core.CvScalar
-dontwarn org.bytedeco.opencv.opencv_core.CvSeq
-dontwarn org.bytedeco.opencv.opencv_core.CvSize2D32f
-dontwarn org.bytedeco.opencv.opencv_core.CvSize
-dontwarn org.bytedeco.opencv.opencv_core.CvSlice
-dontwarn org.bytedeco.opencv.opencv_core.CvTermCriteria
-dontwarn org.bytedeco.opencv.opencv_core.FileNode
-dontwarn org.bytedeco.opencv.opencv_core.FileNodeIterator
-dontwarn org.bytedeco.opencv.opencv_core.FileStorage
-dontwarn org.bytedeco.opencv.opencv_core.IplConvKernel
-dontwarn org.bytedeco.opencv.opencv_core.IplImage
-dontwarn org.bytedeco.opencv.opencv_core.IplROI
-dontwarn org.bytedeco.opencv.opencv_core.KeyPoint
-dontwarn org.bytedeco.opencv.opencv_core.KeyPointVector
-dontwarn org.bytedeco.opencv.opencv_core.Mat
-dontwarn org.bytedeco.opencv.opencv_core.MatVector
-dontwarn org.bytedeco.opencv.opencv_core.Point2f
-dontwarn org.bytedeco.opencv.opencv_core.Point2fVector
-dontwarn org.bytedeco.opencv.opencv_core.Point2fVectorVector
-dontwarn org.bytedeco.opencv.opencv_core.Point3f
-dontwarn org.bytedeco.opencv.opencv_core.Point3fVector
-dontwarn org.bytedeco.opencv.opencv_core.Point3fVectorVector
-dontwarn org.bytedeco.opencv.opencv_core.Point
-dontwarn org.bytedeco.opencv.opencv_core.Rect
-dontwarn org.bytedeco.opencv.opencv_core.Scalar
-dontwarn org.bytedeco.opencv.opencv_core.Size
-dontwarn org.bytedeco.opencv.opencv_core.TermCriteria
-dontwarn org.bytedeco.opencv.opencv_features2d.AKAZE
-dontwarn org.bytedeco.opencv.opencv_flann.Index
-dontwarn org.bytedeco.opencv.opencv_flann.IndexParams
-dontwarn org.bytedeco.opencv.opencv_flann.LshIndexParams
-dontwarn org.bytedeco.opencv.opencv_flann.SearchParams
-dontwarn org.bytedeco.opencv.opencv_imgproc.CvFont
-dontwarn org.bytedeco.opencv.opencv_imgproc.CvMoments
-dontwarn org.bytedeco.opencv.opencv_videoio.VideoCapture
-dontwarn org.bytedeco.opencv.opencv_videoio.VideoWriter
-dontwarn org.bytedeco.videoinput.global.videoInputLib
-dontwarn org.bytedeco.videoinput.videoInput
-dontwarn org.opencv.core.Mat
-dontwarn org.osgi.annotation.bundle.Export
-dontwarn org.osgi.annotation.versioning.ConsumerType