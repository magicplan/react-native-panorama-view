import * as React from "react";
import { requireNativeComponent, ViewStyle, Platform } from "react-native";

export type PanoramaViewProps = {
  imageUrl: string;
  enableTouchTracking?: boolean;
  onImageLoadingFailed?: () => void;
  onImageDownloaded?: () => void;
  onImageLoaded?: () => void;
  style: ViewStyle;
};

export const PanoramaView: React.FC<PanoramaViewProps> = ({
  onImageLoadingFailed,
  onImageDownloaded,
  onImageLoaded,
  ...props
}) => {
  const _onImageLoadingFailed = () => {
    if (onImageLoadingFailed) {
      onImageLoadingFailed();
    }
  };

  const _onImageLoaded = () => {
    if (onImageLoaded) {
      onImageLoaded();
    }
  };

  const _onImageDownloaded = () => {
    if (onImageDownloaded) {
      onImageDownloaded();
    }
  };

  return (
    <NativePanoramaView
      {...props}
      onImageDownloaded={_onImageDownloaded}
      onImageLoaded={_onImageLoaded}
      onImageLoadingFailed={_onImageLoadingFailed}
    />
  );
};

export default PanoramaView;

const NativePanoramaView = requireNativeComponent<PanoramaViewProps>("PanoramaView");
