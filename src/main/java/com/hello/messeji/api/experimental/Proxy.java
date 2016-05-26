// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: proxy.proto

package com.hello.messeji.api.experimental;

public final class Proxy {
  private Proxy() {}
  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistry registry) {
  }
  public interface PayloadWrapperOrBuilder extends
      // @@protoc_insertion_point(interface_extends:PayloadWrapper)
      com.google.protobuf.MessageOrBuilder {

    /**
     * <code>optional .PayloadWrapper.Type type = 1;</code>
     */
    boolean hasType();
    /**
     * <code>optional .PayloadWrapper.Type type = 1;</code>
     */
    com.hello.messeji.api.experimental.Proxy.PayloadWrapper.Type getType();

    /**
     * <code>optional bytes payload = 2;</code>
     */
    boolean hasPayload();
    /**
     * <code>optional bytes payload = 2;</code>
     */
    com.google.protobuf.ByteString getPayload();
  }
  /**
   * Protobuf type {@code PayloadWrapper}
   */
  public static final class PayloadWrapper extends
      com.google.protobuf.GeneratedMessage implements
      // @@protoc_insertion_point(message_implements:PayloadWrapper)
      PayloadWrapperOrBuilder {
    // Use PayloadWrapper.newBuilder() to construct.
    private PayloadWrapper(com.google.protobuf.GeneratedMessage.Builder<?> builder) {
      super(builder);
      this.unknownFields = builder.getUnknownFields();
    }
    private PayloadWrapper(boolean noInit) { this.unknownFields = com.google.protobuf.UnknownFieldSet.getDefaultInstance(); }

    private static final PayloadWrapper defaultInstance;
    public static PayloadWrapper getDefaultInstance() {
      return defaultInstance;
    }

    public PayloadWrapper getDefaultInstanceForType() {
      return defaultInstance;
    }

    private final com.google.protobuf.UnknownFieldSet unknownFields;
    @java.lang.Override
    public final com.google.protobuf.UnknownFieldSet
        getUnknownFields() {
      return this.unknownFields;
    }
    private PayloadWrapper(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      initFields();
      int mutable_bitField0_ = 0;
      com.google.protobuf.UnknownFieldSet.Builder unknownFields =
          com.google.protobuf.UnknownFieldSet.newBuilder();
      try {
        boolean done = false;
        while (!done) {
          int tag = input.readTag();
          switch (tag) {
            case 0:
              done = true;
              break;
            default: {
              if (!parseUnknownField(input, unknownFields,
                                     extensionRegistry, tag)) {
                done = true;
              }
              break;
            }
            case 8: {
              int rawValue = input.readEnum();
              com.hello.messeji.api.experimental.Proxy.PayloadWrapper.Type value = com.hello.messeji.api.experimental.Proxy.PayloadWrapper.Type.valueOf(rawValue);
              if (value == null) {
                unknownFields.mergeVarintField(1, rawValue);
              } else {
                bitField0_ |= 0x00000001;
                type_ = value;
              }
              break;
            }
            case 18: {
              bitField0_ |= 0x00000002;
              payload_ = input.readBytes();
              break;
            }
          }
        }
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        throw e.setUnfinishedMessage(this);
      } catch (java.io.IOException e) {
        throw new com.google.protobuf.InvalidProtocolBufferException(
            e.getMessage()).setUnfinishedMessage(this);
      } finally {
        this.unknownFields = unknownFields.build();
        makeExtensionsImmutable();
      }
    }
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return com.hello.messeji.api.experimental.Proxy.internal_static_PayloadWrapper_descriptor;
    }

    protected com.google.protobuf.GeneratedMessage.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return com.hello.messeji.api.experimental.Proxy.internal_static_PayloadWrapper_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              com.hello.messeji.api.experimental.Proxy.PayloadWrapper.class, com.hello.messeji.api.experimental.Proxy.PayloadWrapper.Builder.class);
    }

    public static com.google.protobuf.Parser<PayloadWrapper> PARSER =
        new com.google.protobuf.AbstractParser<PayloadWrapper>() {
      public PayloadWrapper parsePartialFrom(
          com.google.protobuf.CodedInputStream input,
          com.google.protobuf.ExtensionRegistryLite extensionRegistry)
          throws com.google.protobuf.InvalidProtocolBufferException {
        return new PayloadWrapper(input, extensionRegistry);
      }
    };

    @java.lang.Override
    public com.google.protobuf.Parser<PayloadWrapper> getParserForType() {
      return PARSER;
    }

    /**
     * Protobuf enum {@code PayloadWrapper.Type}
     */
    public enum Type
        implements com.google.protobuf.ProtocolMessageEnum {
      /**
       * <code>RECEIVE_MESSAGES = 1;</code>
       */
      RECEIVE_MESSAGES(0, 1),
      /**
       * <code>BATCH = 2;</code>
       */
      BATCH(1, 2),
      ;

      /**
       * <code>RECEIVE_MESSAGES = 1;</code>
       */
      public static final int RECEIVE_MESSAGES_VALUE = 1;
      /**
       * <code>BATCH = 2;</code>
       */
      public static final int BATCH_VALUE = 2;


      public final int getNumber() { return value; }

      public static Type valueOf(int value) {
        switch (value) {
          case 1: return RECEIVE_MESSAGES;
          case 2: return BATCH;
          default: return null;
        }
      }

      public static com.google.protobuf.Internal.EnumLiteMap<Type>
          internalGetValueMap() {
        return internalValueMap;
      }
      private static com.google.protobuf.Internal.EnumLiteMap<Type>
          internalValueMap =
            new com.google.protobuf.Internal.EnumLiteMap<Type>() {
              public Type findValueByNumber(int number) {
                return Type.valueOf(number);
              }
            };

      public final com.google.protobuf.Descriptors.EnumValueDescriptor
          getValueDescriptor() {
        return getDescriptor().getValues().get(index);
      }
      public final com.google.protobuf.Descriptors.EnumDescriptor
          getDescriptorForType() {
        return getDescriptor();
      }
      public static final com.google.protobuf.Descriptors.EnumDescriptor
          getDescriptor() {
        return com.hello.messeji.api.experimental.Proxy.PayloadWrapper.getDescriptor().getEnumTypes().get(0);
      }

      private static final Type[] VALUES = values();

      public static Type valueOf(
          com.google.protobuf.Descriptors.EnumValueDescriptor desc) {
        if (desc.getType() != getDescriptor()) {
          throw new java.lang.IllegalArgumentException(
            "EnumValueDescriptor is not for this type.");
        }
        return VALUES[desc.getIndex()];
      }

      private final int index;
      private final int value;

      private Type(int index, int value) {
        this.index = index;
        this.value = value;
      }

      // @@protoc_insertion_point(enum_scope:PayloadWrapper.Type)
    }

    private int bitField0_;
    public static final int TYPE_FIELD_NUMBER = 1;
    private com.hello.messeji.api.experimental.Proxy.PayloadWrapper.Type type_;
    /**
     * <code>optional .PayloadWrapper.Type type = 1;</code>
     */
    public boolean hasType() {
      return ((bitField0_ & 0x00000001) == 0x00000001);
    }
    /**
     * <code>optional .PayloadWrapper.Type type = 1;</code>
     */
    public com.hello.messeji.api.experimental.Proxy.PayloadWrapper.Type getType() {
      return type_;
    }

    public static final int PAYLOAD_FIELD_NUMBER = 2;
    private com.google.protobuf.ByteString payload_;
    /**
     * <code>optional bytes payload = 2;</code>
     */
    public boolean hasPayload() {
      return ((bitField0_ & 0x00000002) == 0x00000002);
    }
    /**
     * <code>optional bytes payload = 2;</code>
     */
    public com.google.protobuf.ByteString getPayload() {
      return payload_;
    }

    private void initFields() {
      type_ = com.hello.messeji.api.experimental.Proxy.PayloadWrapper.Type.RECEIVE_MESSAGES;
      payload_ = com.google.protobuf.ByteString.EMPTY;
    }
    private byte memoizedIsInitialized = -1;
    public final boolean isInitialized() {
      byte isInitialized = memoizedIsInitialized;
      if (isInitialized == 1) return true;
      if (isInitialized == 0) return false;

      memoizedIsInitialized = 1;
      return true;
    }

    public void writeTo(com.google.protobuf.CodedOutputStream output)
                        throws java.io.IOException {
      getSerializedSize();
      if (((bitField0_ & 0x00000001) == 0x00000001)) {
        output.writeEnum(1, type_.getNumber());
      }
      if (((bitField0_ & 0x00000002) == 0x00000002)) {
        output.writeBytes(2, payload_);
      }
      getUnknownFields().writeTo(output);
    }

    private int memoizedSerializedSize = -1;
    public int getSerializedSize() {
      int size = memoizedSerializedSize;
      if (size != -1) return size;

      size = 0;
      if (((bitField0_ & 0x00000001) == 0x00000001)) {
        size += com.google.protobuf.CodedOutputStream
          .computeEnumSize(1, type_.getNumber());
      }
      if (((bitField0_ & 0x00000002) == 0x00000002)) {
        size += com.google.protobuf.CodedOutputStream
          .computeBytesSize(2, payload_);
      }
      size += getUnknownFields().getSerializedSize();
      memoizedSerializedSize = size;
      return size;
    }

    private static final long serialVersionUID = 0L;
    @java.lang.Override
    protected java.lang.Object writeReplace()
        throws java.io.ObjectStreamException {
      return super.writeReplace();
    }

    public static com.hello.messeji.api.experimental.Proxy.PayloadWrapper parseFrom(
        com.google.protobuf.ByteString data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static com.hello.messeji.api.experimental.Proxy.PayloadWrapper parseFrom(
        com.google.protobuf.ByteString data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static com.hello.messeji.api.experimental.Proxy.PayloadWrapper parseFrom(byte[] data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static com.hello.messeji.api.experimental.Proxy.PayloadWrapper parseFrom(
        byte[] data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static com.hello.messeji.api.experimental.Proxy.PayloadWrapper parseFrom(java.io.InputStream input)
        throws java.io.IOException {
      return PARSER.parseFrom(input);
    }
    public static com.hello.messeji.api.experimental.Proxy.PayloadWrapper parseFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return PARSER.parseFrom(input, extensionRegistry);
    }
    public static com.hello.messeji.api.experimental.Proxy.PayloadWrapper parseDelimitedFrom(java.io.InputStream input)
        throws java.io.IOException {
      return PARSER.parseDelimitedFrom(input);
    }
    public static com.hello.messeji.api.experimental.Proxy.PayloadWrapper parseDelimitedFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return PARSER.parseDelimitedFrom(input, extensionRegistry);
    }
    public static com.hello.messeji.api.experimental.Proxy.PayloadWrapper parseFrom(
        com.google.protobuf.CodedInputStream input)
        throws java.io.IOException {
      return PARSER.parseFrom(input);
    }
    public static com.hello.messeji.api.experimental.Proxy.PayloadWrapper parseFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return PARSER.parseFrom(input, extensionRegistry);
    }

    public static Builder newBuilder() { return Builder.create(); }
    public Builder newBuilderForType() { return newBuilder(); }
    public static Builder newBuilder(com.hello.messeji.api.experimental.Proxy.PayloadWrapper prototype) {
      return newBuilder().mergeFrom(prototype);
    }
    public Builder toBuilder() { return newBuilder(this); }

    @java.lang.Override
    protected Builder newBuilderForType(
        com.google.protobuf.GeneratedMessage.BuilderParent parent) {
      Builder builder = new Builder(parent);
      return builder;
    }
    /**
     * Protobuf type {@code PayloadWrapper}
     */
    public static final class Builder extends
        com.google.protobuf.GeneratedMessage.Builder<Builder> implements
        // @@protoc_insertion_point(builder_implements:PayloadWrapper)
        com.hello.messeji.api.experimental.Proxy.PayloadWrapperOrBuilder {
      public static final com.google.protobuf.Descriptors.Descriptor
          getDescriptor() {
        return com.hello.messeji.api.experimental.Proxy.internal_static_PayloadWrapper_descriptor;
      }

      protected com.google.protobuf.GeneratedMessage.FieldAccessorTable
          internalGetFieldAccessorTable() {
        return com.hello.messeji.api.experimental.Proxy.internal_static_PayloadWrapper_fieldAccessorTable
            .ensureFieldAccessorsInitialized(
                com.hello.messeji.api.experimental.Proxy.PayloadWrapper.class, com.hello.messeji.api.experimental.Proxy.PayloadWrapper.Builder.class);
      }

      // Construct using com.hello.messeji.api.experimental.Proxy.PayloadWrapper.newBuilder()
      private Builder() {
        maybeForceBuilderInitialization();
      }

      private Builder(
          com.google.protobuf.GeneratedMessage.BuilderParent parent) {
        super(parent);
        maybeForceBuilderInitialization();
      }
      private void maybeForceBuilderInitialization() {
        if (com.google.protobuf.GeneratedMessage.alwaysUseFieldBuilders) {
        }
      }
      private static Builder create() {
        return new Builder();
      }

      public Builder clear() {
        super.clear();
        type_ = com.hello.messeji.api.experimental.Proxy.PayloadWrapper.Type.RECEIVE_MESSAGES;
        bitField0_ = (bitField0_ & ~0x00000001);
        payload_ = com.google.protobuf.ByteString.EMPTY;
        bitField0_ = (bitField0_ & ~0x00000002);
        return this;
      }

      public Builder clone() {
        return create().mergeFrom(buildPartial());
      }

      public com.google.protobuf.Descriptors.Descriptor
          getDescriptorForType() {
        return com.hello.messeji.api.experimental.Proxy.internal_static_PayloadWrapper_descriptor;
      }

      public com.hello.messeji.api.experimental.Proxy.PayloadWrapper getDefaultInstanceForType() {
        return com.hello.messeji.api.experimental.Proxy.PayloadWrapper.getDefaultInstance();
      }

      public com.hello.messeji.api.experimental.Proxy.PayloadWrapper build() {
        com.hello.messeji.api.experimental.Proxy.PayloadWrapper result = buildPartial();
        if (!result.isInitialized()) {
          throw newUninitializedMessageException(result);
        }
        return result;
      }

      public com.hello.messeji.api.experimental.Proxy.PayloadWrapper buildPartial() {
        com.hello.messeji.api.experimental.Proxy.PayloadWrapper result = new com.hello.messeji.api.experimental.Proxy.PayloadWrapper(this);
        int from_bitField0_ = bitField0_;
        int to_bitField0_ = 0;
        if (((from_bitField0_ & 0x00000001) == 0x00000001)) {
          to_bitField0_ |= 0x00000001;
        }
        result.type_ = type_;
        if (((from_bitField0_ & 0x00000002) == 0x00000002)) {
          to_bitField0_ |= 0x00000002;
        }
        result.payload_ = payload_;
        result.bitField0_ = to_bitField0_;
        onBuilt();
        return result;
      }

      public Builder mergeFrom(com.google.protobuf.Message other) {
        if (other instanceof com.hello.messeji.api.experimental.Proxy.PayloadWrapper) {
          return mergeFrom((com.hello.messeji.api.experimental.Proxy.PayloadWrapper)other);
        } else {
          super.mergeFrom(other);
          return this;
        }
      }

      public Builder mergeFrom(com.hello.messeji.api.experimental.Proxy.PayloadWrapper other) {
        if (other == com.hello.messeji.api.experimental.Proxy.PayloadWrapper.getDefaultInstance()) return this;
        if (other.hasType()) {
          setType(other.getType());
        }
        if (other.hasPayload()) {
          setPayload(other.getPayload());
        }
        this.mergeUnknownFields(other.getUnknownFields());
        return this;
      }

      public final boolean isInitialized() {
        return true;
      }

      public Builder mergeFrom(
          com.google.protobuf.CodedInputStream input,
          com.google.protobuf.ExtensionRegistryLite extensionRegistry)
          throws java.io.IOException {
        com.hello.messeji.api.experimental.Proxy.PayloadWrapper parsedMessage = null;
        try {
          parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
          parsedMessage = (com.hello.messeji.api.experimental.Proxy.PayloadWrapper) e.getUnfinishedMessage();
          throw e;
        } finally {
          if (parsedMessage != null) {
            mergeFrom(parsedMessage);
          }
        }
        return this;
      }
      private int bitField0_;

      private com.hello.messeji.api.experimental.Proxy.PayloadWrapper.Type type_ = com.hello.messeji.api.experimental.Proxy.PayloadWrapper.Type.RECEIVE_MESSAGES;
      /**
       * <code>optional .PayloadWrapper.Type type = 1;</code>
       */
      public boolean hasType() {
        return ((bitField0_ & 0x00000001) == 0x00000001);
      }
      /**
       * <code>optional .PayloadWrapper.Type type = 1;</code>
       */
      public com.hello.messeji.api.experimental.Proxy.PayloadWrapper.Type getType() {
        return type_;
      }
      /**
       * <code>optional .PayloadWrapper.Type type = 1;</code>
       */
      public Builder setType(com.hello.messeji.api.experimental.Proxy.PayloadWrapper.Type value) {
        if (value == null) {
          throw new NullPointerException();
        }
        bitField0_ |= 0x00000001;
        type_ = value;
        onChanged();
        return this;
      }
      /**
       * <code>optional .PayloadWrapper.Type type = 1;</code>
       */
      public Builder clearType() {
        bitField0_ = (bitField0_ & ~0x00000001);
        type_ = com.hello.messeji.api.experimental.Proxy.PayloadWrapper.Type.RECEIVE_MESSAGES;
        onChanged();
        return this;
      }

      private com.google.protobuf.ByteString payload_ = com.google.protobuf.ByteString.EMPTY;
      /**
       * <code>optional bytes payload = 2;</code>
       */
      public boolean hasPayload() {
        return ((bitField0_ & 0x00000002) == 0x00000002);
      }
      /**
       * <code>optional bytes payload = 2;</code>
       */
      public com.google.protobuf.ByteString getPayload() {
        return payload_;
      }
      /**
       * <code>optional bytes payload = 2;</code>
       */
      public Builder setPayload(com.google.protobuf.ByteString value) {
        if (value == null) {
    throw new NullPointerException();
  }
  bitField0_ |= 0x00000002;
        payload_ = value;
        onChanged();
        return this;
      }
      /**
       * <code>optional bytes payload = 2;</code>
       */
      public Builder clearPayload() {
        bitField0_ = (bitField0_ & ~0x00000002);
        payload_ = getDefaultInstance().getPayload();
        onChanged();
        return this;
      }

      // @@protoc_insertion_point(builder_scope:PayloadWrapper)
    }

    static {
      defaultInstance = new PayloadWrapper(true);
      defaultInstance.initFields();
    }

    // @@protoc_insertion_point(class_scope:PayloadWrapper)
  }

  private static final com.google.protobuf.Descriptors.Descriptor
    internal_static_PayloadWrapper_descriptor;
  private static
    com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internal_static_PayloadWrapper_fieldAccessorTable;

  public static com.google.protobuf.Descriptors.FileDescriptor
      getDescriptor() {
    return descriptor;
  }
  private static com.google.protobuf.Descriptors.FileDescriptor
      descriptor;
  static {
    java.lang.String[] descriptorData = {
      "\n\013proxy.proto\"n\n\016PayloadWrapper\022\"\n\004type\030" +
      "\001 \001(\0162\024.PayloadWrapper.Type\022\017\n\007payload\030\002" +
      " \001(\014\"\'\n\004Type\022\024\n\020RECEIVE_MESSAGES\020\001\022\t\n\005BA" +
      "TCH\020\002B$\n\"com.hello.messeji.api.experimen" +
      "tal"
    };
    com.google.protobuf.Descriptors.FileDescriptor.InternalDescriptorAssigner assigner =
        new com.google.protobuf.Descriptors.FileDescriptor.    InternalDescriptorAssigner() {
          public com.google.protobuf.ExtensionRegistry assignDescriptors(
              com.google.protobuf.Descriptors.FileDescriptor root) {
            descriptor = root;
            return null;
          }
        };
    com.google.protobuf.Descriptors.FileDescriptor
      .internalBuildGeneratedFileFrom(descriptorData,
        new com.google.protobuf.Descriptors.FileDescriptor[] {
        }, assigner);
    internal_static_PayloadWrapper_descriptor =
      getDescriptor().getMessageTypes().get(0);
    internal_static_PayloadWrapper_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessage.FieldAccessorTable(
        internal_static_PayloadWrapper_descriptor,
        new java.lang.String[] { "Type", "Payload", });
  }

  // @@protoc_insertion_point(outer_class_scope)
}