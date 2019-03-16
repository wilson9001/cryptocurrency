import java.util.*;

public class TxHandler {

    private UTXOPool unspentTransactions;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        unspentTransactions = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {

        HashMap<UTXO, Transaction.Output> newlyCreatedTransactions = new HashMap<>();
        HashMap<UTXO, Transaction.Output> newlySpentTransactions = new HashMap<>();
        double totalOutputValue = 0;
        double totalInputValue = 0;
        boolean validTransaction = true;

        if(tx.numInputs() == 0)
        {
            return false;
        }

        //save these unspent transactions until they are all confirmed, removing them from the unspent transaction pool as you go.
        // If the transaction is invalid, add them back into the set of unspent transactions.
        Transaction.Input currentInput;

        for (int inputIndex = 0; inputIndex < tx.numInputs(); inputIndex++)
        {
            currentInput = tx.getInput(inputIndex);

            UTXO unspentTransactionReference = new UTXO(currentInput.prevTxHash, currentInput.outputIndex);

            Transaction.Output output = unspentTransactions.getTxOutput(unspentTransactionReference);

            if(output != null)
            {
                if(Crypto.verifySignature(output.address, tx.getRawDataToSign(inputIndex), currentInput.signature))
                {
                    //aggregate input values and add to the set of newly spent transaction outputs after determining them to be valid
                    totalInputValue += output.value;

                    newlySpentTransactions.put(unspentTransactionReference, output);
                    unspentTransactions.removeUTXO(unspentTransactionReference);
                }
                else
                {
                    validTransaction = false;
                    break;
                }
            }
            else
            {
                validTransaction = false;
                break;
            }
        }

        if(validTransaction)
        {
            Transaction.Output currentOutput;

            //verify outputs are positive and aggregate value to verify later that in total they are <= sum of the inputs
            for (int outputIndex = 0; outputIndex < tx.numOutputs(); outputIndex++) {
                currentOutput = tx.getOutput(outputIndex);

                if (currentOutput.value > 0) {
                    totalOutputValue += currentOutput.value;
                    //add to set of newly created transaction outputs
                    newlyCreatedTransactions.put(new UTXO(tx.getHash(), outputIndex), currentOutput);
                } else {
                    validTransaction = false;
                    break;
                }

            }
        }

        //make sure outputs don't exceed inputs
        if(validTransaction && totalOutputValue > totalInputValue)
        {
            validTransaction = false;
        }

        if(validTransaction)
        {
            //transaction is confirmed valid, add newly spent transaction outputs and newly created transaction outputs to their respective pools
            for(UTXO newlyCreatedUnspentTransaction : newlyCreatedTransactions.keySet())
            {
                unspentTransactions.addUTXO(newlyCreatedUnspentTransaction, newlyCreatedTransactions.get(newlyCreatedUnspentTransaction));
            }
        }
        else
        {
            //transaction was not valid, add newly spent transaction outputs back to pool of unspent transactions
            for(UTXO transactionToAddBackToPool : newlySpentTransactions.keySet())
            {
                unspentTransactions.addUTXO(transactionToAddBackToPool, newlySpentTransactions.get(transactionToAddBackToPool));
            }
        }

        return validTransaction;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        ArrayList<Transaction> potentialDoubleSpends = new ArrayList<>();
        ArrayList<Transaction> validTransactions = new ArrayList<>();

        //iterate through transactions once and save any potential double spends.
        for(Transaction transaction : possibleTxs)
        {
            if(isValidTx(transaction))
            {
                //outputs from this transaction have been added to the newly created unspent transaction pool.
                //old outputs that were expended in this transaction were removed from the unspent pool.
                validTransactions.add(transaction);
            }
            else
            {
                //nothing was changed in the unspent transaction pool.
                potentialDoubleSpends.add(transaction);
            }
        }

        int potentialDoubleSpendCount = potentialDoubleSpends.size();
        int oldPotentialDoubleSpendCount;

        //iterate over any potential double spends after adding the unspent transactions of the confirmed transactions to the unspent set.
        do {
            for (Transaction potentialDoubleSpend : potentialDoubleSpends)
            {
                //add any that now return true to the set. This will maintain a valid ordering of the transactions as well if later transactions reference earlier ones
                if(isValidTx(potentialDoubleSpend))
                {
                    validTransactions.add(potentialDoubleSpend);
                    potentialDoubleSpends.remove(potentialDoubleSpend);
                }
            }

            oldPotentialDoubleSpendCount = potentialDoubleSpendCount;
            potentialDoubleSpendCount = potentialDoubleSpends.size();
        } while (potentialDoubleSpendCount != oldPotentialDoubleSpendCount);
        // If there were transactions added, iterate over the set again. Repeat until there is no change in the set.

        Transaction[] finalValidTransactions = new Transaction[validTransactions.size()];
        validTransactions.toArray(finalValidTransactions);
        return finalValidTransactions;
    }

}
