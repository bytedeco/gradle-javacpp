/*
 * Copyright (C) 2009-2018 Samuel Audet
 *
 * Licensed either under the Apache License, Version 2.0, or (at your option)
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation (subject to the "Classpath" exception),
 * either version 2, or any later version (collectively, the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *     http://www.gnu.org/licenses/
 *     http://www.gnu.org/software/classpath/license.html
 *
 * or as provided in the LICENSE.txt file that accompanied this code.
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.bytedeco.javacv.samples

import org.bytedeco.javacpp.Loader
import org.bytedeco.javacpp.indexer.DoubleIndexer
import org.bytedeco.javacv.CanvasFrame
import org.bytedeco.javacv.FrameGrabber
import org.bytedeco.javacv.FrameRecorder
import org.bytedeco.javacv.OpenCVFrameConverter.ToMat
import org.bytedeco.opencv.global.opencv_calib3d
import org.bytedeco.opencv.global.opencv_core
import org.bytedeco.opencv.global.opencv_imgproc
import org.bytedeco.opencv.opencv_core.*
import org.bytedeco.opencv.opencv_objdetect.CascadeClassifier
import java.net.URL


object Demo {
    @Throws(Exception::class)
    @JvmStatic
    fun main(args: Array<String>) {
        val classifierName = if (args.size > 0) {
            args[0]
        } else {
            val url = URL("https://raw.github.com/opencv/opencv/master/data/haarcascades/haarcascade_frontalface_alt.xml")
            val file = Loader.cacheResource(url)
            file.absolutePath
        }

        // We can "cast" Pointer objects by instantiating a new object of the desired class.
        val classifier = CascadeClassifier(classifierName)

        // The available FrameGrabber classes include OpenCVFrameGrabber (opencv_videoio),
        // DC1394FrameGrabber, FlyCapture2FrameGrabber, OpenKinectFrameGrabber, OpenKinect2FrameGrabber,
        // RealSenseFrameGrabber, RealSense2FrameGrabber, PS3EyeFrameGrabber, VideoInputFrameGrabber, and FFmpegFrameGrabber.
        val grabber = FrameGrabber.createDefault(0)
        grabber.start()

        // CanvasFrame, FrameGrabber, and FrameRecorder use Frame objects to communicate image data.
        // We need a FrameConverter to interface with other APIs (Android, Java 2D, JavaFX, Tesseract, OpenCV, etc).
        val converter = ToMat()

        // FAQ about IplImage and Mat objects from OpenCV:
        // - For custom raw processing of data, createBuffer() returns an NIO direct
        //   buffer wrapped around the memory pointed by imageData, and under Android we can
        //   also use that Buffer with Bitmap.copyPixelsFromBuffer() and copyPixelsToBuffer().
        // - To get a BufferedImage from an IplImage, or vice versa, we can chain calls to
        //   Java2DFrameConverter and OpenCVFrameConverter, one after the other.
        // - Java2DFrameConverter also has static copy() methods that we can use to transfer
        //   data more directly between BufferedImage and IplImage or Mat via Frame objects.
        var grabbedImage = converter.convert(grabber.grab())
        val height = grabbedImage.rows()
        val width = grabbedImage.cols()

        // Objects allocated with `new`, clone(), or a create*() factory method are automatically released
        // by the garbage collector, but may still be explicitly released by calling deallocate().
        // You shall NOT call cvReleaseImage(), cvReleaseMemStorage(), etc. on objects allocated this way.
        val grayImage = Mat(height, width, opencv_core.CV_8UC1)
        val rotatedImage = grabbedImage.clone()

        // The OpenCVFrameRecorder class simply uses the VideoWriter of opencv_videoio,
        // but FFmpegFrameRecorder also exists as a more versatile alternative.
        val recorder = FrameRecorder.createDefault("output.avi", width, height)
        recorder.start()

        // CanvasFrame is a JFrame containing a Canvas component, which is hardware accelerated.
        // It can also switch into full-screen mode when called with a screenNumber.
        // We should also specify the relative monitor/camera response for proper gamma correction.
        val frame = CanvasFrame("Some Title", CanvasFrame.getDefaultGamma() / grabber.gamma)

        // Let's create some random 3D rotation...
        val randomR = Mat(3, 3, opencv_core.CV_64FC1)
        val randomAxis = Mat(3, 1, opencv_core.CV_64FC1)
        // We can easily and efficiently access the elements of matrices and images
        // through an Indexer object with the set of get() and put() methods.
        val Ridx = randomR.createIndexer<DoubleIndexer>()
        val axisIdx = randomAxis.createIndexer<DoubleIndexer>()
        axisIdx.put(0, (Math.random() - 0.5) / 4,
                (Math.random() - 0.5) / 4,
                (Math.random() - 0.5) / 4)
        opencv_calib3d.Rodrigues(randomAxis, randomR)
        val f = (width + height) / 2.0
        Ridx.put(0, 2, Ridx[0, 2] * f)
        Ridx.put(1, 2, Ridx[1, 2] * f)
        Ridx.put(2, 0, Ridx[2, 0] / f)
        Ridx.put(2, 1, Ridx[2, 1] / f)
        println(Ridx)

        // We can allocate native arrays using constructors taking an integer as argument.
        val hatPoints = Point(3)
        while (frame.isVisible && converter.convert(grabber.grab()).also { grabbedImage = it } != null) {
            // Let's try to detect some faces! but we need a grayscale image...
            opencv_imgproc.cvtColor(grabbedImage, grayImage, opencv_imgproc.CV_BGR2GRAY)
            val faces = RectVector()
            classifier.detectMultiScale(grayImage, faces)
            val total = faces.size()
            for (i in 0 until total) {
                val r = faces[i]
                val x = r.x()
                val y = r.y()
                val w = r.width()
                val h = r.height()
                opencv_imgproc.rectangle(grabbedImage, Point(x, y), Point(x + w, y + h), Scalar.RED, 1, opencv_imgproc.CV_AA, 0)

                // To access or pass as argument the elements of a native array, call position() before.
                hatPoints.position(0).x(x - w / 10).y(y - h / 10)
                hatPoints.position(1).x(x + w * 11 / 10).y(y - h / 10)
                hatPoints.position(2).x(x + w / 2).y(y - h / 2)
                opencv_imgproc.fillConvexPoly(grabbedImage, hatPoints.position(0), 3, Scalar.GREEN, opencv_imgproc.CV_AA, 0)
            }

            // Let's find some contours! but first some thresholding...
            opencv_imgproc.threshold(grayImage, grayImage, 64.0, 255.0, opencv_imgproc.CV_THRESH_BINARY)

            // To check if an output argument is null we may call either isNull() or equals(null).
            val contours = MatVector()
            opencv_imgproc.findContours(grayImage, contours, opencv_imgproc.CV_RETR_LIST, opencv_imgproc.CV_CHAIN_APPROX_SIMPLE)
            val n = contours.size()
            for (i in 0 until n) {
                val contour = contours[i]
                val points = Mat()
                opencv_imgproc.approxPolyDP(contour, points, opencv_imgproc.arcLength(contour, true) * 0.02, true)
                opencv_imgproc.drawContours(grabbedImage, MatVector(points), -1, Scalar.BLUE)
            }
            opencv_imgproc.warpPerspective(grabbedImage, rotatedImage, randomR, rotatedImage.size())
            val rotatedFrame = converter.convert(rotatedImage)
            frame.showImage(rotatedFrame)
            recorder.record(rotatedFrame)
        }
        frame.dispose()
        recorder.stop()
        grabber.stop()
    }
}