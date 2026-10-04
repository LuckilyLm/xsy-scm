declare module 'sm-crypto' {
  const smCrypto: {
    sm4: {
      encrypt(value: string, key: string): string;
      decrypt(value: string, key: string): string;
    };
  };

  export default smCrypto;
}
