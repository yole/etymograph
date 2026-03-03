import {useFormContext} from "react-hook-form";
import {FormFieldProps} from "@/components/FormRow";

export interface FormCheckboxProps extends FormFieldProps {
    handleChange?: (data: any) => void
}

export default function FormCheckbox(props: FormCheckboxProps) {
    const {register, getValues} = useFormContext()
    return <>
        <input type="checkbox"
               {...register(props.id, {onChange: () => {
                   if (props.handleChange !== undefined) props.handleChange(getValues())}
               })}/>
        {props.label}
        <br/></>
}
