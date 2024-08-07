package io.github.snower.jaslock.spring.boot;

import java.io.*;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public interface SlockSerializater {
    byte[] serializate(Object object) throws IOException;
    Object deserialize(byte[] data, Class<?> clsType) throws IOException;
    Object deserialize(byte[] data, TypeReference<?> typeReference) throws IOException;

    abstract class TypeReference<T> implements Comparable<TypeReference<T>> {
        protected final Type _type;

        protected TypeReference() {
            Type superClass = this.getClass().getGenericSuperclass();
            if (superClass instanceof Class) {
                throw new IllegalArgumentException("Internal error: TypeReference constructed without actual type information");
            } else {
                this._type = ((ParameterizedType)superClass).getActualTypeArguments()[0];
            }
        }

        public Type getType() {
            return this._type;
        }

        public int compareTo(TypeReference<T> o) {
            return 0;
        }
    }

    class ObjectSerializater implements SlockSerializater {

        @Override
        public byte[] serializate(Object object) throws IOException {
            try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
                try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)) {
                    objectOutputStream.writeObject(object);
                }
                return byteArrayOutputStream.toByteArray();
            }
        }

        @Override
        public Object deserialize(byte[] data, Class<?> clsType) throws IOException {
            try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(data))) {
                return objectInputStream.readObject();
            } catch (ClassNotFoundException e) {
                throw new IOException(e);
            }
        }

        @Override
        public Object deserialize(byte[] data, TypeReference<?> typeReference) throws IOException {
            try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(data))) {
                return objectInputStream.readObject();
            } catch (ClassNotFoundException e) {
                throw new IOException(e);
            }
        }
    }
}
